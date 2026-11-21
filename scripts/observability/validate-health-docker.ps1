[CmdletBinding()]
param(
    [string]$ComposeFile = "backend/docker/observability-compose.yml",
    [int]$ManagementContainerPort = 8081,
    [string]$BackendService = "backend",
    [string]$CollectorService = "collector-probe",
    [string]$UnauthorizedService = "unauthorized-probe",
    [string]$AuthorizedNetwork = "observability"
)

$ErrorActionPreference = "Stop"

function Write-PassMessage {
    param([string]$Message)
    Write-Host "PASS: $Message" -ForegroundColor Green
}

function Stop-WithError {
    param([string]$Message)
    throw "ERROR: $Message"
}

function Invoke-DockerCheck {
    param(
        [string[]]$Arguments,
        [string]$FailureMessage
    )

    $null = & docker @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError $FailureMessage
    }
}

function Get-ComposeServices {
    $result = @(& docker compose -f $ComposeFile config --services 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Docker Compose no pudo leer $ComposeFile."
    }
    return @($result | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-ComposeConfiguration {
    $result = @(& docker compose -f $ComposeFile config --format json 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "Docker Compose no pudo generar su configuración efectiva."
    }
    try {
        return (($result -join "`n") | ConvertFrom-Json)
    } catch {
        Stop-WithError "La configuración efectiva de Docker Compose no es JSON válido."
    }
}

function Get-DeclaredServiceNetworks {
    param(
        [object]$ComposeConfiguration,
        [string]$Service
    )

    $serviceProperty = $ComposeConfiguration.services.PSObject.Properties[$Service]
    if ($null -eq $serviceProperty) {
        Stop-WithError "El servicio $Service no está definido en la configuración efectiva."
    }
    $declaredNetworks = $serviceProperty.Value.networks
    if ($null -eq $declaredNetworks) {
        return @()
    }
    if ($declaredNetworks -is [System.Management.Automation.PSCustomObject]) {
        return @($declaredNetworks.PSObject.Properties.Name)
    }
    return @($declaredNetworks | ForEach-Object { [string]$_ })
}

function Get-RunningContainerId {
    param([string]$Service)

    $containerId = (& docker compose -f $ComposeFile ps --status running -q $Service 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        Stop-WithError "El servicio $Service no está ejecutándose."
    }
    return $containerId
}

function Get-ContainerNetworks {
    param([string]$ContainerId)

    $result = @(& docker inspect --format '{{json .NetworkSettings.Networks}}' $ContainerId 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "No se pudo inspeccionar la red del contenedor."
    }
    try {
        return (($result -join "`n") | ConvertFrom-Json)
    } catch {
        Stop-WithError "La información de redes del contenedor no es válida."
    }
}

function Get-NetworkProperty {
    param(
        [object]$Networks,
        [string]$NetworkName
    )

    $property = $Networks.PSObject.Properties[$NetworkName]
    if ($null -eq $property) {
        Stop-WithError "El contenedor no está conectado a la red autorizada."
    }
    return $property.Value
}

function Get-ContainerPublishedPorts {
    param([string]$ContainerId)

    $result = @(& docker inspect --format '{{json .NetworkSettings.Ports}}' $ContainerId 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "No se pudieron inspeccionar los puertos publicados del backend."
    }

    $json = $result -join "`n"
    if ([string]::IsNullOrWhiteSpace($json) -or $json.Trim() -eq "null") {
        return [pscustomobject]@{}
    }

    try {
        return $json | ConvertFrom-Json
    } catch {
        Stop-WithError "La información de puertos publicados no es JSON válido."
    }
}

function Get-PublishedPortBindings {
    param(
        [object]$PublishedPorts,
        [int]$ContainerPort
    )

    $portProperty = $PublishedPorts.PSObject.Properties["${ContainerPort}/tcp"]
    if ($null -eq $portProperty -or $null -eq $portProperty.Value) {
        return @()
    }

    return @($portProperty.Value | Where-Object {
        $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_.HostPort)
    })
}

function Get-ActualAuthorizedNetworkName {
    param(
        [object]$Networks,
        [string]$DeclaredNetworkName
    )

    $property = $Networks.PSObject.Properties |
        Where-Object { $_.Name -eq $DeclaredNetworkName -or $_.Name -match "(?:^|[_-])${DeclaredNetworkName}$" } |
        Select-Object -First 1
    if ($null -eq $property) {
        Stop-WithError "No se encontró la red efectiva $DeclaredNetworkName en el backend."
    }
    return $property.Name
}

function Invoke-CollectorHealth {
    param([string]$Url)

    $marker = "__HEALTH_STATUS__"
    $result = @(& docker compose -f $ComposeFile run --rm --no-deps $CollectorService curl `
        --silent --show-error --output - --write-out "`n${marker}:%{http_code}" `
        --connect-timeout 3 --max-time 5 $Url 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "collector-probe no pudo completar la solicitud HTTP de health."
    }

    $output = $result -join "`n"
    $match = [regex]::Match($output, "(?s)^(.*)\r?\n${marker}:(\d{3})\s*$")
    if (-not $match.Success) {
        Stop-WithError "No se pudo determinar el código HTTP de health desde collector-probe."
    }

    $statusCode = [int]$match.Groups[2].Value
    $body = $match.Groups[1].Value.Trim()
    if ($statusCode -ne 200) {
        Stop-WithError "collector-probe recibió HTTP $statusCode en health."
    }

    try {
        $json = $body | ConvertFrom-Json
    } catch {
        Stop-WithError "collector-probe recibió un cuerpo de health que no es JSON válido."
    }

    $status = [string]$json.status
    if ($status -notin @("UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN")) {
        Stop-WithError "La respuesta de health no contiene un estado interpretable."
    }
    if ($json.PSObject.Properties.Name -contains "details") {
        Stop-WithError "La respuesta de health expone details."
    }
    if ($body -match '(?i)(password|secret|token|api[-_]?key|authorization|jdbc:|postgres(?:ql)?://|mysql://)') {
        Stop-WithError "La respuesta de health contiene información sensible."
    }

    Write-PassMessage "collector-probe obtuvo health HTTP 200, JSON válido y sin detalles sensibles."
}

function Assert-NoTcpConnectionFromCompose {
    param(
        [string]$Service,
        [string]$Url
    )

    $curlExecutionMarker = "__NEGATIVE_CURL_EXECUTED__"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& docker compose -f $ComposeFile run --rm --no-deps $Service curl `
            --silent --show-error --output /dev/null --write-out "${curlExecutionMarker}:%{http_code}" `
            --connect-timeout 3 --max-time 5 $Url 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $capturedOutput = $output -join "`n"
    if ($capturedOutput -notmatch [regex]::Escape($curlExecutionMarker)) {
        Stop-WithError "$Service no confirmó la ejecución de curl; se considera un fallo de Docker Compose o de infraestructura."
    }
    if ($exitCode -eq 0) {
        Stop-WithError "$Service estableció una conexión TCP con el puerto administrativo."
    }
    if ($exitCode -notin @(7, 28)) {
        Stop-WithError "$Service produjo un error de infraestructura no concluyente (curl $exitCode)."
    }

    Write-PassMessage "$Service no pudo establecer conectividad con el puerto administrativo."
}

function Assert-NoTcpConnectionFromHost {
    param([string]$Url)

    if ($null -eq (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
        Stop-WithError "curl.exe no está disponible en el host Windows."
    }

    $curlExecutionMarker = "__NEGATIVE_CURL_EXECUTED__"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& curl.exe --silent --show-error --output NUL `
            --write-out "${curlExecutionMarker}:%{http_code}" `
            --connect-timeout 3 --max-time 5 $Url 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $capturedOutput = $output -join "`n"
    if ($capturedOutput -notmatch [regex]::Escape($curlExecutionMarker)) {
        Stop-WithError "El host no confirmó la ejecución de curl; se considera un fallo de infraestructura."
    }
    if ($exitCode -eq 0) {
        Stop-WithError "El host estableció una conexión TCP con el puerto administrativo."
    }
    if ($exitCode -notin @(7, 28)) {
        Stop-WithError "El host produjo un error local no concluyente (curl $exitCode)."
    }

    Write-PassMessage "El host no pudo establecer conectividad con el puerto administrativo."
}

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    Stop-WithError "No existe el archivo Compose $ComposeFile."
}
if ($ManagementContainerPort -le 0) {
    Stop-WithError "El puerto administrativo configurado debe ser positivo."
}
if ($null -eq (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
    Stop-WithError "docker.exe no está disponible en el host Windows."
}

Invoke-DockerCheck -Arguments @("version") -FailureMessage "Docker no responde correctamente."
Invoke-DockerCheck -Arguments @("compose", "version") -FailureMessage "Docker Compose no responde correctamente."
Write-PassMessage "Docker y Docker Compose están disponibles."

$services = Get-ComposeServices
foreach ($requiredService in @($BackendService, $CollectorService, $UnauthorizedService)) {
    if ($services -notcontains $requiredService) {
        Stop-WithError "El servicio requerido $requiredService no está definido en Compose."
    }
}
Write-PassMessage "Los servicios requeridos están definidos en Compose."

$composeConfiguration = Get-ComposeConfiguration
$backendDeclaredNetworks = Get-DeclaredServiceNetworks $composeConfiguration $BackendService
$collectorDeclaredNetworks = Get-DeclaredServiceNetworks $composeConfiguration $CollectorService
$unauthorizedDeclaredNetworks = Get-DeclaredServiceNetworks $composeConfiguration $UnauthorizedService
if ($backendDeclaredNetworks -notcontains $AuthorizedNetwork -or
    $collectorDeclaredNetworks -notcontains $AuthorizedNetwork) {
    Stop-WithError "Backend y collector-probe no comparten la red autorizada declarada."
}
if ($unauthorizedDeclaredNetworks -contains $AuthorizedNetwork) {
    Stop-WithError "unauthorized-probe pertenece a la red autorizada declarada."
}
Write-PassMessage "La topología declarada separa la red autorizada de unauthorized-probe."

$backendId = Get-RunningContainerId $BackendService
$backendNetworks = Get-ContainerNetworks $backendId
$actualAuthorizedNetwork = Get-ActualAuthorizedNetworkName $backendNetworks $AuthorizedNetwork
$authorizedNetworkProperty = Get-NetworkProperty $backendNetworks $actualAuthorizedNetwork
$backendIp = [string]$authorizedNetworkProperty.IPAddress
if ([string]::IsNullOrWhiteSpace($backendIp)) {
    Stop-WithError "No se pudo obtener dinámicamente la IP del backend en la red autorizada."
}

$publishedPorts = Get-ContainerPublishedPorts $backendId
$managementBindings = Get-PublishedPortBindings $publishedPorts $ManagementContainerPort
if ($managementBindings.Count -gt 0) {
    Stop-WithError "El puerto administrativo está publicado al host."
}
Write-PassMessage "El puerto administrativo no está publicado al host."

$collectorId = (& docker compose -f $ComposeFile ps --status running -q $CollectorService 2>$null).Trim()
if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($collectorId)) {
    $collectorNetworks = Get-ContainerNetworks $collectorId
    $null = Get-NetworkProperty $collectorNetworks $actualAuthorizedNetwork
    Write-PassMessage "Backend y collector-probe están conectados a la red autorizada efectiva."
} else {
    Write-Host "PASS: collector-probe será creado temporalmente por la prueba HTTP." -ForegroundColor Green
}

$collectorUrl = "http://${BackendService}:${ManagementContainerPort}/actuator/health"
Invoke-CollectorHealth $collectorUrl

$null = & docker compose -f $ComposeFile run --rm --no-deps $UnauthorizedService curl --version 2>$null
if ($LASTEXITCODE -ne 0) {
    Stop-WithError "unauthorized-probe no pudo ejecutar curl."
}
Write-PassMessage "unauthorized-probe puede ejecutarse para la prueba negativa."

$managementUrlByIp = "http://${backendIp}:${ManagementContainerPort}/actuator/health"
Assert-NoTcpConnectionFromCompose $UnauthorizedService $managementUrlByIp
Assert-NoTcpConnectionFromHost $managementUrlByIp

Write-PassMessage "Validación de health Docker completada sin violaciones detectadas."
