[CmdletBinding()]
param(
    [string]$ComposeFile = "backend/docker/observability-compose.yml",
    [string]$ScenariosDirectory = "backend/docker/observability-scenarios",
    [string[]]$Scenario = @("all"),
    [int]$StartupTimeoutSeconds = 90,
    [int]$PublishedManagementHostPort = 18081,
    [int]$HostReachablePort = 18082
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$ComposeFile = Join-Path $repositoryRoot $ComposeFile
$ScenariosDirectory = Join-Path $repositoryRoot $ScenariosDirectory
$minimumComposeVersion = [version]"2.24.4"
$cleanupTimeoutSeconds = 20

$scenarioFiles = [ordered]@{
    "invalid-listener" = "invalid-management-listener.compose.yml"
    "invalid-port" = "invalid-management-port.compose.yml"
    "loopback-listener" = "loopback-management-listener.compose.yml"
    "published-management-port" = "published-management-port.compose.yml"
    "backend-second-network" = "backend-second-network.compose.yml"
    "collector-outside-private" = "collector-outside-private.compose.yml"
    "host-reachable" = "host-reachable.compose.yml"
    "broad-chain" = "unsafe-broad-chain.compose.yml"
}

function Write-PassMessage {
    param([string]$Message)

    Write-Host "PASS: $Message" -ForegroundColor Green
}

function Stop-WithError {
    param([string]$Message)

    throw "FAIL: $Message"
}

function Invoke-DockerNative {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $output = @()
    $exitCode = -1
    try {
        $ErrorActionPreference = "Continue"
        if ($null -ne (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
            try {
                $output = @(& docker.exe @Arguments 2>&1)
                $exitCode = $LASTEXITCODE
            } catch {
                $exitCode = -1
            }
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
    }
}

function Invoke-ScenarioCompose {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string[]]$Arguments
    )

    $composeArguments = @("compose", "--project-name", $ProjectName, "-f", $ComposeFile,
        "-f", $ScenarioFile) + $Arguments
    return Invoke-DockerNative $composeArguments
}

function ConvertTo-ValidPort {
    param(
        [string]$Value,
        [string]$Name
    )

    $port = 0
    if (-not [int]::TryParse($Value, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
        Stop-WithError "$Name debe ser un puerto TCP entre 1 y 65535."
    }
    return $port
}

function Get-BusinessPublishedPort {
    $configuredPort = $env:BUSINESS_PUBLISHED_PORT
    if ([string]::IsNullOrWhiteSpace($configuredPort)) {
        return 18080
    }
    return ConvertTo-ValidPort $configuredPort "BUSINESS_PUBLISHED_PORT"
}

function Assert-PortConfiguration {
    $businessPort = Get-BusinessPublishedPort
    $publishedPort = ConvertTo-ValidPort $PublishedManagementHostPort "PublishedManagementHostPort"
    $hostReachablePort = ConvertTo-ValidPort $HostReachablePort "HostReachablePort"
    if ($publishedPort -eq $hostReachablePort) {
        Stop-WithError "Los dos puertos administrativos negativos deben ser distintos."
    }
    if ($publishedPort -eq $businessPort -or $hostReachablePort -eq $businessPort) {
        Stop-WithError "Los puertos administrativos negativos no pueden coincidir con BUSINESS_PUBLISHED_PORT."
    }
}

function Assert-DockerAvailable {
    $result = Invoke-DockerNative @("version", "--format", "{{.Server.Version}}")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "docker.exe no esta disponible o el daemon no responde."
    }
}

function Assert-ComposeOverrideCompatibility {
    $result = Invoke-DockerNative @("compose", "version", "--short")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo determinar la version de Docker Compose."
    }
    $match = [regex]::Match($result.Output, "(?<version>\d+\.\d+\.\d+)")
    if (-not $match.Success) {
        Stop-WithError "Docker Compose no devolvio una version compatible."
    }
    $composeVersion = [version]$match.Groups["version"].Value
    if ($composeVersion -lt $minimumComposeVersion) {
        Stop-WithError "Docker Compose debe ser >= $minimumComposeVersion para usar !override."
    }
}

function Get-ScenarioFile {
    param([string]$ScenarioName)

    $fileName = $scenarioFiles[$ScenarioName]
    if ($null -eq $fileName) {
        Stop-WithError "Escenario no reconocido: $ScenarioName"
    }
    $file = Join-Path $ScenariosDirectory $fileName
    if (-not (Test-Path $file)) {
        Stop-WithError "Falta el fixture del escenario solicitado."
    }
    return $file
}

function New-ScenarioProjectName {
    param([string]$ScenarioName)

    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
    return "obs-negative-$ScenarioName-$suffix"
}

function Assert-ComposePreflight {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @("config", "-q")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "El preflight de Docker Compose fallo para un overlay negativo."
    }
}

function Get-ScenarioContainerId {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string]$ServiceName
    )

    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @("ps", "--all", "-q", $ServiceName)
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Output)) {
        Stop-WithError "No se encontro un contenedor temporal esperado."
    }
    return $result.Output.Trim()
}

function Get-ContainerState {
    param([string]$ContainerId)

    $result = Invoke-DockerNative @("inspect", "--format", "{{.State.Status}}", $ContainerId)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo inspeccionar el estado de un contenedor temporal."
    }
    return $result.Output.Trim()
}

function Get-ContainerHealth {
    param([string]$ContainerId)

    $result = Invoke-DockerNative @("inspect", "--format", "{{if .State.Health}}{{.State.Health.Status}}{{end}}", $ContainerId)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo inspeccionar la salud de un contenedor temporal."
    }
    return $result.Output.Trim()
}

function Get-ScenarioServiceLogs {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string]$ServiceName
    )

    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @("logs", "--no-color", $ServiceName)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudieron leer los logs del contenedor temporal."
    }
    return $result.Output
}

function Wait-ForContainerState {
    param(
        [string]$ContainerId,
        [string[]]$ExpectedStates
    )

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        $state = Get-ContainerState $ContainerId
        if ($ExpectedStates -contains $state) {
            return $state
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Stop-WithError "Un contenedor temporal no alcanzo el estado esperado."
}

function Wait-ForHealthyPostgres {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $postgresId = Get-ScenarioContainerId $ProjectName $ScenarioFile "postgres"
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        $state = Get-ContainerState $postgresId
        $health = Get-ContainerHealth $postgresId
        if ($state -eq "running" -and $health -eq "healthy") {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Stop-WithError "PostgreSQL temporal no estuvo saludable; no se puede atribuir el fallo al listener."
}

function Wait-ForManagementListener {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $command = "if command -v ss >/dev/null 2>&1; then ss -lnt; " +
        "elif command -v netstat >/dev/null 2>&1; then netstat -lnt; " +
        "else cat /proc/net/tcp /proc/net/tcp6; fi"
    $portHex = "{0:X4}" -f 8081
    $socketPattern = "(?m)(?:[\[\]*0-9A-Fa-f:.]+):8081(?:\s|$)"
    $procPattern = "(?mi)^\s*\d+:\s*[0-9A-F]{8,32}:${portHex}\s+[0-9A-F]{8,32}:[0-9A-F]{4}\s+0A\b"
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @(
            "exec", "-T", "backend", "sh", "-c", $command)
        if ($result.ExitCode -ne 0) {
            Stop-WithError "Docker exec fallo al comprobar el listener administrativo [category=docker-exec-listener; exitCode=$($result.ExitCode)]."
        }
        if ($result.Output -match $socketPattern -or $result.Output -match $procPattern) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Stop-WithError "El listener administrativo temporal no estuvo listo."
}

function Wait-ForRunningService {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string]$ServiceName
    )

    $containerId = Get-ScenarioContainerId $ProjectName $ScenarioFile $ServiceName
    Wait-ForContainerState $containerId @("running") | Out-Null
    return $containerId
}

function Wait-ForReadyBackend {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $backendId = Wait-ForRunningService $ProjectName $ScenarioFile "backend"
    Wait-ForManagementListener $ProjectName $ScenarioFile
    return $backendId
}

function Start-Scenario {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string[]]$Services = @()
    )

    $arguments = @("up", "--build", "--detach") + $Services
    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile $arguments
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo iniciar el proyecto temporal del escenario [category=compose-up-build-start; exitCode=$($result.ExitCode)]."
    }
}

function Stop-Scenario {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @(
        "down", "--remove-orphans", "--timeout", $cleanupTimeoutSeconds)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "La limpieza del proyecto temporal fallo [category=compose-cleanup; exitCode=$($result.ExitCode)]."
    }

    Assert-ScenarioContainersRemoved $ProjectName $ScenarioFile
}

function Assert-ScenarioContainersRemoved {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @("ps", "--all", "--quiet")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo confirmar la limpieza del proyecto temporal $ProjectName [category=compose-cleanup-verification; exitCode=$($result.ExitCode)]."
    }

    if (-not [string]::IsNullOrWhiteSpace($result.Output)) {
        Stop-WithError "La limpieza del proyecto temporal dejo contenedores residuales [$ProjectName] [category=compose-cleanup-residual-containers]."
    }
}

function Get-ContainerNetworkNames {
    param([string]$ContainerId)

    $result = Invoke-DockerNative @("inspect", "--format", "{{json .NetworkSettings.Networks}}", $ContainerId)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudieron inspeccionar las redes temporales."
    }
    $networks = $result.Output | ConvertFrom-Json
    return @($networks.PSObject.Properties.Name)
}

function Find-NetworkName {
    param(
        [string[]]$NetworkNames,
        [string]$DeclaredName
    )

    $suffix = [regex]::Escape($DeclaredName)
    $networkName = $NetworkNames | Where-Object {
        $_ -eq $DeclaredName -or $_ -match "(?:^|[_-])${suffix}$"
    } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($networkName)) {
        Stop-WithError "No existe la red temporal esperada."
    }
    return $networkName
}

function Assert-ExactNetworks {
    param(
        [string[]]$ActualNetworks,
        [string[]]$ExpectedDeclaredNetworks
    )

    $expectedNetworks = @($ExpectedDeclaredNetworks | ForEach-Object {
            Find-NetworkName $ActualNetworks $_
        } | Sort-Object)
    $actualSorted = @($ActualNetworks | Sort-Object)
    $differences = @(Compare-Object $expectedNetworks $actualSorted)
    if ($actualSorted.Count -ne $expectedNetworks.Count -or $differences.Count -ne 0) {
        Stop-WithError "Las redes efectivas del escenario no coinciden con el aislamiento esperado."
    }
}

function Get-HostHttpStatus {
    param([string]$Url)

    $status = & curl.exe --silent --output NUL --write-out "%{http_code}" --connect-timeout 3 --max-time 10 $Url 2>$null
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "El host no pudo completar la comprobacion HTTP temporal."
    }
    return ($status -join "").Trim()
}

function Get-CollectorConnectivity {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string]$Path
    )

    $command = "status=`$(curl --silent --output /dev/null --write-out '%{http_code}' --connect-timeout 3 --max-time 5 http://backend:8081$Path); curlExit=`$?; printf '%s|%s' `"`$curlExit`" `"`$status`"; exit 0"
    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @(
        "exec", "-T", "collector-probe", "sh", "-c", $command)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "Docker exec fallo durante la comprobacion de conectividad."
    }
    $match = [regex]::Match($result.Output.Trim(), "^(?<exit>\d+)\|(?<status>\d{3})$")
    if (-not $match.Success) {
        Stop-WithError "curl no devolvio un resultado de conectividad interpretable."
    }
    return [pscustomobject]@{
        CurlExit = [int]$match.Groups["exit"].Value
        HttpStatus = $match.Groups["status"].Value
    }
}

function Assert-ExpectedNetworkFailure {
    param([object]$Connectivity)

    $expectedExitCodes = @(6, 7, 28)
    if ($expectedExitCodes -notcontains $Connectivity.CurlExit -or $Connectivity.HttpStatus -ne "000") {
        Stop-WithError "La comprobacion no produjo un fallo DNS o TCP esperado; no se acepta como aislamiento."
    }
}

function Assert-HttpOkFromHost {
    param([string]$Url)

    $status = Get-HostHttpStatus $Url
    if ($status -ne "200") {
        Stop-WithError "El endpoint administrativo temporal no devolvio HTTP 200."
    }
}

function Assert-ExpectedStartupSignature {
    param(
        [string]$ScenarioName,
        [string]$Logs
    )

    $matchesExpectedFailure = switch ($ScenarioName) {
        "invalid-listener" {
            $Logs -match "(?i)invalid-management-listener" -and
                $Logs -match "(?i)(unknownhost|bindexception|cannot assign requested address|failed to bind)"
        }
        "invalid-port" {
            ($Logs -match "(?i)(management\.server\.port|management_server_port)" -and
                $Logs -match "(?i)(70000|out of range|invalid port|failed to bind properties)") -or
                $Logs -match "(?i)(port out of range|invalid port).{0,40}70000" -or
                $Logs -match "(?i)70000.{0,40}(port out of range|invalid port)"
        }
        default { $false }
    }
    if (-not $matchesExpectedFailure) {
        Stop-WithError "El backend no fallo con la firma esperada de configuracion o bind."
    }
}

function Assert-PublishedManagementPort {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    $result = Invoke-ScenarioCompose $ProjectName $ScenarioFile @("port", "backend", "8081")
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Output)) {
        Stop-WithError "El escenario no publico el puerto administrativo esperado."
    }
}

function Assert-SecondNetworkCanReachManagement {
    param([string]$NetworkName)

    $result = Invoke-DockerNative @(
        "run", "--rm", "--network", $NetworkName, "curlimages/curl:8.11.1",
        "--silent", "--output", "/dev/null", "--write-out", "%{http_code}",
        "--connect-timeout", "3", "--max-time", "10", "http://backend:8081/actuator/health")
    if ($result.ExitCode -ne 0 -or $result.Output.Trim() -ne "200") {
        Stop-WithError "El probe temporal de la segunda red no alcanzo health con HTTP 200."
    }
}

function Test-InvalidStartup {
    param(
        [string]$ScenarioName,
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    Start-Scenario $ProjectName $ScenarioFile @("backend")
    Wait-ForHealthyPostgres $ProjectName $ScenarioFile
    $backendId = Get-ScenarioContainerId $ProjectName $ScenarioFile "backend"
    Wait-ForContainerState $backendId @("exited", "dead") | Out-Null
    $logs = Get-ScenarioServiceLogs $ProjectName $ScenarioFile "backend"
    Assert-ExpectedStartupSignature $ScenarioName $logs
    Write-PassMessage "$ScenarioName produjo la firma esperada de configuracion o bind."
}

function Get-SanitizedFailureDiagnostic {
    param(
        [System.Management.Automation.ErrorRecord]$ErrorRecord,
        [string]$Phase
    )

    $message = $ErrorRecord.Exception.Message
    $categoryMatch = [regex]::Match($message, "category=(?<category>[a-z-]+)")
    $exitCodeMatch = [regex]::Match($message, "exitCode=(?<exitCode>-?\d+)")
    $category = if ($categoryMatch.Success) { $categoryMatch.Groups["category"].Value } else { "validation-check" }
    $exitCode = if ($exitCodeMatch.Success) { $exitCodeMatch.Groups["exitCode"].Value } else { "n/a" }
    return "phase=$Phase; category=$category; exitCode=$exitCode"
}

function Test-CollectorIsolation {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile,
        [string[]]$ExpectedCollectorNetworks
    )

    Start-Scenario $ProjectName $ScenarioFile @("collector-probe")
    $backendId = Wait-ForReadyBackend $ProjectName $ScenarioFile
    $collectorId = Wait-ForRunningService $ProjectName $ScenarioFile "collector-probe"
    Assert-ExactNetworks (Get-ContainerNetworkNames $backendId) @("observability")
    Assert-ExactNetworks (Get-ContainerNetworkNames $collectorId) $ExpectedCollectorNetworks
    $connectivity = Get-CollectorConnectivity $ProjectName $ScenarioFile "/actuator/health"
    Assert-ExpectedNetworkFailure $connectivity
    Write-PassMessage "collector-probe quedo aislado con un fallo TCP o DNS verificable."
}

function Test-PublishedManagementPort {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    Start-Scenario $ProjectName $ScenarioFile @("backend")
    Wait-ForReadyBackend $ProjectName $ScenarioFile | Out-Null
    Assert-PublishedManagementPort $ProjectName $ScenarioFile
    Assert-HttpOkFromHost "http://127.0.0.1:$PublishedManagementHostPort/actuator/health"
    Write-PassMessage "publicacion administrativa y acceso real desde host detectados."
}

function Test-BackendSecondNetwork {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    Start-Scenario $ProjectName $ScenarioFile @("backend")
    $backendId = Wait-ForReadyBackend $ProjectName $ScenarioFile
    $backendNetworks = Get-ContainerNetworkNames $backendId
    Assert-ExactNetworks $backendNetworks @("observability", "business-ingress")
    $ingressNetwork = Find-NetworkName $backendNetworks "business-ingress"
    Assert-SecondNetworkCanReachManagement $ingressNetwork
    Write-PassMessage "segunda red y alcance administrativo detectados."
}

function Test-HostReachable {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    Start-Scenario $ProjectName $ScenarioFile @("backend")
    Wait-ForReadyBackend $ProjectName $ScenarioFile | Out-Null
    Assert-HttpOkFromHost "http://127.0.0.1:$HostReachablePort/actuator/health"
    Write-PassMessage "host alcanzable para administracion detectado mediante HTTP real."
}

function Test-BroadSecurityChain {
    param(
        [string]$ProjectName,
        [string]$ScenarioFile
    )

    Start-Scenario $ProjectName $ScenarioFile @("collector-probe")
    $backendId = Wait-ForReadyBackend $ProjectName $ScenarioFile
    $collectorId = Wait-ForRunningService $ProjectName $ScenarioFile "collector-probe"
    Assert-ExactNetworks (Get-ContainerNetworkNames $backendId) @("observability")
    Assert-ExactNetworks (Get-ContainerNetworkNames $collectorId) @("observability")
    $connectivity = Get-CollectorConnectivity $ProjectName $ScenarioFile "/actuator/env"
    if ($connectivity.CurlExit -ne 0 -or $connectivity.HttpStatus -ne "200") {
        Stop-WithError "La cadena amplia no expuso /actuator/env mediante HTTP 200."
    }
    Write-PassMessage "cadena Actuator amplia detectada por acceso a /actuator/env."
}

function Invoke-NegativeScenario {
    param([string]$ScenarioName)

    $scenarioFile = Get-ScenarioFile $ScenarioName
    $projectName = New-ScenarioProjectName $ScenarioName
    $validationFailed = $false
    $validationDiagnostic = $null
    $cleanupDiagnostic = $null
    try {
        switch ($ScenarioName) {
            "invalid-listener" { Test-InvalidStartup $ScenarioName $projectName $scenarioFile }
            "invalid-port" { Test-InvalidStartup $ScenarioName $projectName $scenarioFile }
            "loopback-listener" { Test-CollectorIsolation $projectName $scenarioFile @("observability") }
            "published-management-port" { Test-PublishedManagementPort $projectName $scenarioFile }
            "backend-second-network" { Test-BackendSecondNetwork $projectName $scenarioFile }
            "collector-outside-private" { Test-CollectorIsolation $projectName $scenarioFile @("unauthorized") }
            "host-reachable" { Test-HostReachable $projectName $scenarioFile }
            "broad-chain" { Test-BroadSecurityChain $projectName $scenarioFile }
        }
    } catch {
        $validationFailed = $true
        $validationDiagnostic = Get-SanitizedFailureDiagnostic $_ "validation"
    } finally {
        $cleanupFailed = $false
        try {
            Stop-Scenario $projectName $scenarioFile
        } catch {
            $cleanupFailed = $true
            $cleanupDiagnostic = Get-SanitizedFailureDiagnostic $_ "cleanup"
        }
        if ($cleanupFailed -and $validationFailed) {
            Stop-WithError "La validacion y la limpieza temporal fallaron [$validationDiagnostic] [$cleanupDiagnostic]."
        }
        if ($cleanupFailed) {
            Stop-WithError "La limpieza temporal fallo [$cleanupDiagnostic]."
        }
    }
    if ($validationFailed) {
        Stop-WithError "La validacion del escenario $ScenarioName no cumplio su criterio negativo esperado [$validationDiagnostic]."
    }
}

if (-not (Test-Path $ComposeFile)) {
    Stop-WithError "No existe el Compose seguro."
}

$selectedScenarios = if ($Scenario -contains "all") { @($scenarioFiles.Keys) } else { $Scenario }
Assert-PortConfiguration
Assert-DockerAvailable
Assert-ComposeOverrideCompatibility

foreach ($scenarioName in $selectedScenarios) {
    $scenarioFile = Get-ScenarioFile $scenarioName
    Assert-ComposePreflight (New-ScenarioProjectName "preflight") $scenarioFile
}

$previousPublishedPort = $env:UNSAFE_MANAGEMENT_PUBLISHED_PORT
$previousHostReachablePort = $env:UNSAFE_HOST_REACHABLE_PORT
$env:UNSAFE_MANAGEMENT_PUBLISHED_PORT = $PublishedManagementHostPort
$env:UNSAFE_HOST_REACHABLE_PORT = $HostReachablePort
try {
    foreach ($scenarioName in $selectedScenarios) {
        Invoke-NegativeScenario $scenarioName
    }
} finally {
    $env:UNSAFE_MANAGEMENT_PUBLISHED_PORT = $previousPublishedPort
    $env:UNSAFE_HOST_REACHABLE_PORT = $previousHostReachablePort
}

Write-PassMessage "Todos los escenarios negativos fueron detectados sin tocar el proyecto Compose normal."
