[CmdletBinding()]
param(
    [string]$ComposeFile = "backend/docker/observability-compose.yml",
    [ValidateRange(1, 65535)]
    [int]$ManagementContainerPort = 8081,
    [ValidateRange(1, 65535)]
    [int]$BusinessContainerPort = 8080,
    [string]$BackendService = "backend",
    [string]$GatewayService = "business-gateway",
    [string]$PostgresService = "postgres",
    [string]$CollectorService = "collector-probe",
    [string]$UnauthorizedService = "unauthorized-probe",
    [string]$AuthorizedNetwork = "observability",
    [string]$UnauthorizedNetwork = "unauthorized",
    [string]$BusinessIngressNetwork = "business-ingress",
    [string]$BusinessProtectedPath = "/api/v1/market/transactions"
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

function Invoke-DockerNative {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output -join "`n"
    }
}

function Get-ComposeConfiguration {
    $result = Invoke-DockerNative -Arguments @("compose", "-f", $ComposeFile, "config", "--format", "json")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "Docker Compose no pudo generar la configuracion efectiva."
    }

    try {
        return $result.Output | ConvertFrom-Json
    } catch {
        Stop-WithError "La configuracion efectiva de Docker Compose no es JSON valido."
    }
}

function Get-DeclaredServiceNetworks {
    param(
        [object]$ComposeConfiguration,
        [string]$Service
    )

    $serviceProperty = $ComposeConfiguration.services.PSObject.Properties[$Service]
    if ($null -eq $serviceProperty) {
        Stop-WithError "El servicio $Service no esta definido en Compose."
    }

    $networks = $serviceProperty.Value.networks
    if ($null -eq $networks) {
        return @()
    }
    if ($networks -is [System.Management.Automation.PSCustomObject]) {
        return @($networks.PSObject.Properties.Name)
    }
    return @($networks | ForEach-Object { [string]$_ })
}

function Get-RunningContainerId {
    param([string]$Service)

    $result = Invoke-DockerNative -Arguments @(
        "compose", "-f", $ComposeFile, "ps", "--status", "running", "-q", $Service
    )
    $containerId = $result.Output.Trim()
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        Stop-WithError "El servicio $Service no esta ejecutandose."
    }
    return $containerId
}

function Get-ContainerNetworks {
    param([string]$ContainerId)

    $result = Invoke-DockerNative -Arguments @(
        "inspect", "--format", "{{json .NetworkSettings.Networks}}", $ContainerId
    )
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo inspeccionar la red del contenedor $ContainerId."
    }

    try {
        return $result.Output | ConvertFrom-Json
    } catch {
        Stop-WithError "La informacion de redes del contenedor $ContainerId no es JSON valido."
    }
}

function Get-ActualNetworkName {
    param(
        [object]$Networks,
        [string]$DeclaredNetworkName
    )

    $suffix = [regex]::Escape($DeclaredNetworkName)
    $property = $Networks.PSObject.Properties |
        Where-Object { $_.Name -eq $DeclaredNetworkName -or $_.Name -match "(?:^|[_-])${suffix}$" } |
        Select-Object -First 1
    if ($null -eq $property) {
        Stop-WithError "No se encontro la red efectiva $DeclaredNetworkName."
    }
    return $property.Name
}

function Assert-ExclusiveNetwork {
    param(
        [object]$Networks,
        [string]$ExpectedNetwork,
        [string]$Service
    )

    $networkNames = @($Networks.PSObject.Properties.Name)
    if ($networkNames.Count -ne 1 -or $networkNames[0] -ne $ExpectedNetwork) {
        Stop-WithError "$Service debe estar conectado exclusivamente a $ExpectedNetwork."
    }
}

function Assert-InternalNetwork {
    param([string]$NetworkName)

    $result = Invoke-DockerNative -Arguments @(
        "network", "inspect", "--format", "{{.Internal}}", $NetworkName
    )
    if ($result.ExitCode -ne 0 -or $result.Output.Trim().ToLowerInvariant() -ne "true") {
        Stop-WithError "La red efectiva $NetworkName no esta marcada como interna."
    }
}

function Assert-NonInternalNetwork {
    param([string]$NetworkName)

    $result = Invoke-DockerNative -Arguments @(
        "network", "inspect", "--format", "{{.Internal}}", $NetworkName
    )
    if ($result.ExitCode -ne 0 -or $result.Output.Trim().ToLowerInvariant() -ne "false") {
        Stop-WithError "La red efectiva $NetworkName debe estar marcada como no interna."
    }
}

function Assert-ExactNetworkNames {
    param(
        [string[]]$ActualNetworks,
        [string[]]$ExpectedNetworks,
        [string]$Service
    )

    $actualNetworks = @($ActualNetworks | Sort-Object)
    $expectedNetworks = @($ExpectedNetworks | Sort-Object)
    $differences = @(Compare-Object -ReferenceObject $expectedNetworks -DifferenceObject $actualNetworks)
    if ($actualNetworks.Count -ne $expectedNetworks.Count -or $differences.Count -ne 0) {
        Stop-WithError "$Service no tiene exactamente las redes esperadas."
    }
}

function Assert-ExactNetworks {
    param(
        [object]$Networks,
        [string[]]$ExpectedNetworks,
        [string]$Service
    )

    Assert-ExactNetworkNames -ActualNetworks @($Networks.PSObject.Properties.Name) `
        -ExpectedNetworks $ExpectedNetworks -Service $Service
}

function Get-ContainerPublishedPorts {
    param([string]$ContainerId)

    $result = Invoke-DockerNative -Arguments @(
        "inspect", "--format", "{{json .NetworkSettings.Ports}}", $ContainerId
    )
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudieron inspeccionar los puertos publicados del contenedor $ContainerId."
    }

    if ([string]::IsNullOrWhiteSpace($result.Output) -or $result.Output.Trim() -eq "null") {
        return [pscustomobject]@{}
    }

    try {
        return $result.Output | ConvertFrom-Json
    } catch {
        Stop-WithError "La informacion de puertos publicados no es JSON valido."
    }
}

function Get-PublishedPortBindings {
    param(
        [object]$PublishedPorts,
        [int]$ContainerPort
    )

    $property = $PublishedPorts.PSObject.Properties["${ContainerPort}/tcp"]
    if ($null -eq $property -or $null -eq $property.Value) {
        return @()
    }
    return @($property.Value | Where-Object {
        $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_.HostPort)
    })
}

function Get-PublishedPortBindingEntries {
    param([object]$PublishedPorts)

    $entries = @()
    foreach ($property in @($PublishedPorts.PSObject.Properties)) {
        foreach ($binding in @($property.Value | Where-Object {
                    $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_.HostPort)
                })) {
            $entries += [pscustomobject]@{
                ContainerPort = $property.Name
                HostPort      = [string]$binding.HostPort
            }
        }
    }
    return $entries
}

function Assert-NoPublishedPorts {
    param(
        [object]$PublishedPorts,
        [string]$Description
    )

    $entries = @(Get-PublishedPortBindingEntries $PublishedPorts)
    if ($entries.Count -ne 0) {
        Stop-WithError "$Description tiene puertos publicados."
    }
}

function Get-ExclusivelyPublishedBusinessPort {
    param([object]$PublishedPorts)

    $entries = @(Get-PublishedPortBindingEntries $PublishedPorts)
    if ($entries.Count -eq 0 -or @($entries | Where-Object {
                $_.ContainerPort -ne "${BusinessContainerPort}/tcp"
            }).Count -ne 0) {
        Stop-WithError "El gateway no publica exclusivamente el puerto de negocio."
    }

    $hostPorts = @($entries | ForEach-Object { $_.HostPort } | Select-Object -Unique)
    if ($hostPorts.Count -ne 1) {
        Stop-WithError "No se pudo determinar un unico puerto de negocio publicado por el gateway."
    }
    return $hostPorts[0]
}

function Assert-NoPublishedPortCommand {
    param(
        [string[]]$Arguments,
        [object]$PublishedPorts,
        [int]$ContainerPort,
        [string]$Description
    )

    $result = Invoke-DockerNative -Arguments $Arguments
    $output = $result.Output.Trim()
    if ($result.ExitCode -eq 0 -and $output -eq ":0") {
        $bindings = @(Get-PublishedPortBindings -PublishedPorts $PublishedPorts -ContainerPort $ContainerPort)
        if ($bindings.Count -eq 0) {
            Write-PassMessage "$Description confirma que el puerto no esta publicado."
            return
        }
    }
    if ($result.ExitCode -eq 0 -or -not [string]::IsNullOrWhiteSpace($output) -and
            $output -notmatch "(?i)no (?:public )?port") {
        Stop-WithError "$Description no confirmo de forma concluyente la ausencia de publicacion."
    }
    if ($output -notmatch "(?i)no (?:public )?port") {
        Stop-WithError "$Description devolvio un diagnostico no reconocido."
    }

    Write-PassMessage "$Description confirma que el puerto no esta publicado."
}

function Assert-PublishedPortCommand {
    param(
        [string[]]$Arguments,
        [string]$ExpectedHostPort,
        [string]$Description
    )

    $result = Invoke-DockerNative -Arguments $Arguments
    $output = $result.Output.Trim()
    $hostPortPattern = "(?m):$([regex]::Escape($ExpectedHostPort))(?:\s*$)"
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($output) -or $output -notmatch $hostPortPattern) {
        Stop-WithError "$Description no confirma la publicacion efectiva del puerto de negocio esperado."
    }
}

function Assert-ManagementListener {
    param([string]$ContainerId)

    $command = "if command -v ss >/dev/null 2>&1; then ss -lnt; " +
        "elif command -v netstat >/dev/null 2>&1; then netstat -lnt; " +
        "else cat /proc/net/tcp /proc/net/tcp6; fi"
    $result = Invoke-DockerNative -Arguments @("exec", $ContainerId, "sh", "-c", $command)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo inspeccionar el listener administrativo dentro del backend."
    }

    $portHex = "{0:X4}" -f $ManagementContainerPort
    $socketPattern = "(?m)(?:[\[\]*0-9A-Fa-f:.]+):${ManagementContainerPort}(?:\s|$)"
    $procPattern = "(?mi)^\s*\d+:\s*[0-9A-F]{8,32}:${portHex}\s+[0-9A-F]{8,32}:[0-9A-F]{4}\s+0A\b"
    if ($result.Output -notmatch $socketPattern -and $result.Output -notmatch $procPattern) {
        Stop-WithError "No se encontro un listener TCP para el puerto administrativo $ManagementContainerPort."
    }

    Write-PassMessage "El listener administrativo $ManagementContainerPort esta activo dentro del backend."
}

function Assert-GatewayRoutingConfiguration {
    param([string]$ContainerId)

    $result = Invoke-DockerNative -Arguments @("exec", $ContainerId, "nginx", "-T")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo inspeccionar la configuracion efectiva del gateway."
    }

    $proxyPasses = @([regex]::Matches($result.Output, "(?m)^\s*proxy_pass\s+([^;]+);\s*$"))
    if ($proxyPasses.Count -ne 1 -or $proxyPasses[0].Groups[1].Value.Trim() -ne "http://backend:8080" -or
            $result.Output -match "backend:8081" -or $result.Output -match '(?m)^\s*proxy_pass\s+.*\$') {
        Stop-WithError "El gateway no tiene un unico upstream estatico hacia backend:8080."
    }
    if ($result.Output -notmatch '(?m)^\s*proxy_set_header Authorization \$http_authorization;\s*$' -or
            $result.Output -notmatch '(?m)^\s*proxy_set_header X-API-Key \$http_x_api_key;\s*$') {
        Stop-WithError "El gateway no conserva explicitamente las cabeceras de autenticacion."
    }

    Write-PassMessage "El gateway tiene un unico upstream estatico hacia backend:8080."
}

function Assert-HttpOkFromService {
    param(
        [string]$Service,
        [string]$Url
    )

    $marker = "__HTTP_STATUS__"
    $result = Invoke-DockerNative -Arguments @(
        "compose", "-f", $ComposeFile, "exec", "-T", $Service, "curl",
        "--silent", "--show-error", "--output", "/dev/null", "--write-out", "${marker}:%{http_code}",
        "--connect-timeout", "3", "--max-time", "5", $Url
    )
    if ($result.ExitCode -ne 0 -or $result.Output -notmatch "${marker}:200") {
        Stop-WithError "$Service no obtuvo HTTP 200 desde $Url."
    }
}

function Assert-NoTcpConnectionFromService {
    param(
        [string]$Service,
        [string]$Url
    )

    $marker = "__NEGATIVE_CURL_EXECUTED__"
    $result = Invoke-DockerNative -Arguments @(
        "compose", "-f", $ComposeFile, "exec", "-T", $Service, "curl",
        "--silent", "--show-error", "--output", "/dev/null", "--write-out", "${marker}:%{http_code}",
        "--connect-timeout", "3", "--max-time", "5", $Url
    )
    if ($result.Output -notmatch [regex]::Escape($marker)) {
        Stop-WithError "$Service no confirmo la ejecucion de la comprobacion TCP."
    }
    if ($result.ExitCode -eq 0) {
        Stop-WithError "$Service establecio una conexion TCP con el puerto administrativo."
    }
    if ($result.ExitCode -notin @(7, 28)) {
        Stop-WithError "$Service produjo un error de conectividad no concluyente (curl $($result.ExitCode))."
    }
}

function Assert-NoTcpConnectionFromHost {
    param([string]$Url)

    if ($null -eq (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
        Stop-WithError "curl.exe no esta disponible en el host Windows."
    }

    $marker = "__NEGATIVE_CURL_EXECUTED__"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& curl.exe --silent --show-error --output NUL --write-out "${marker}:%{http_code}" `
            --connect-timeout 3 --max-time 5 $Url 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $capturedOutput = $output -join "`n"
    if ($capturedOutput -notmatch [regex]::Escape($marker)) {
        Stop-WithError "El host no confirmo la ejecucion de la comprobacion TCP."
    }
    if ($exitCode -eq 0) {
        Stop-WithError "El host establecio una conexion TCP con el puerto administrativo."
    }
    if ($exitCode -notin @(7, 28)) {
        Stop-WithError "El host produjo un error de conectividad no concluyente (curl $exitCode)."
    }
}

function Assert-ActuatorDeniedOnBusinessPort {
    param(
        [int]$PublishedPort,
        [string]$Path
    )

    $marker = "__BUSINESS_STATUS__"
    $url = "http://127.0.0.1:${PublishedPort}${Path}"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& curl.exe --silent --show-error --output NUL --write-out "${marker}:%{http_code}" `
            --connect-timeout 3 --max-time 5 $url 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $capturedOutput = $output -join "`n"
    $statusMatch = [regex]::Match($capturedOutput, "${marker}:(\d{3})")
    if ($exitCode -ne 0 -or -not $statusMatch.Success) {
        Stop-WithError "No se pudo comprobar $Path mediante el puerto de negocio publicado."
    }
    $statusCode = [int]$statusMatch.Groups[1].Value
    if ($statusCode -notin @(401, 403, 404)) {
        Stop-WithError "$Path recibio HTTP $statusCode desde el puerto de negocio publicado."
    }
}

function Assert-ProtectedBusinessRouteRejectedWithoutCredentials {
    param([int]$PublishedPort)

    $marker = "__BUSINESS_PROTECTED_STATUS__"
    $url = "http://127.0.0.1:${PublishedPort}${BusinessProtectedPath}"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& curl.exe --silent --show-error --output NUL --write-out "${marker}:%{http_code}" `
                --connect-timeout 3 --max-time 5 $url 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $statusMatch = [regex]::Match(($output -join "`n"), "${marker}:(\d{3})")
    if ($exitCode -ne 0 -or -not $statusMatch.Success) {
        Stop-WithError "No se pudo comprobar el rechazo sin credenciales de $BusinessProtectedPath."
    }
    if ([int]$statusMatch.Groups[1].Value -notin @(401, 403)) {
        Stop-WithError "$BusinessProtectedPath no conserva el rechazo sin credenciales a traves del gateway."
    }
}

function Assert-OptionalAuthorizedBusinessRoute {
    param([int]$PublishedPort)

    $authorization = [string]$env:OBSERVABILITY_BUSINESS_AUTHORIZATION
    if ([string]::IsNullOrWhiteSpace($authorization)) {
        Write-Host "SKIP: La comprobacion positiva opcional JWT de negocio fue omitida sin OBSERVABILITY_BUSINESS_AUTHORIZATION." -ForegroundColor Yellow
        return
    }

    $headers = @{ Authorization = $authorization }

    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:${PublishedPort}${BusinessProtectedPath}" `
            -Headers $headers -Method Get -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
    } catch {
        Stop-WithError "La ruta protegida no conservo una respuesta autorizada a traves del gateway."
    }
    if ($response.StatusCode -ne 200) {
        Stop-WithError "La ruta protegida no devolvio HTTP 200 a traves del gateway."
    }

    Write-PassMessage "La ruta protegida conserva la autorizacion a traves del gateway."
}

function Invoke-Validation {
    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        Stop-WithError "No existe el archivo Compose $ComposeFile."
    }
    if ($null -eq (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
        Stop-WithError "docker.exe no esta disponible en el host Windows."
    }

    $dockerVersion = Invoke-DockerNative -Arguments @("version")
    $composeVersion = Invoke-DockerNative -Arguments @("compose", "version")
    if ($dockerVersion.ExitCode -ne 0 -or $composeVersion.ExitCode -ne 0) {
        Stop-WithError "Docker o Docker Compose no responde correctamente."
    }

    $composeConfiguration = Get-ComposeConfiguration
    $backendDeclaredNetworks = @(Get-DeclaredServiceNetworks $composeConfiguration $BackendService)
    $gatewayDeclaredNetworks = @(Get-DeclaredServiceNetworks $composeConfiguration $GatewayService)
    $postgresDeclaredNetworks = @(Get-DeclaredServiceNetworks $composeConfiguration $PostgresService)
    $collectorDeclaredNetworks = @(Get-DeclaredServiceNetworks $composeConfiguration $CollectorService)
    $unauthorizedDeclaredNetworks = @(Get-DeclaredServiceNetworks $composeConfiguration $UnauthorizedService)
    Assert-ExactNetworkNames -ActualNetworks $backendDeclaredNetworks -ExpectedNetworks @($AuthorizedNetwork) `
        -Service $BackendService
    Assert-ExactNetworkNames -ActualNetworks $postgresDeclaredNetworks -ExpectedNetworks @($AuthorizedNetwork) `
        -Service $PostgresService
    Assert-ExactNetworkNames -ActualNetworks $collectorDeclaredNetworks -ExpectedNetworks @($AuthorizedNetwork) `
        -Service $CollectorService
    Assert-ExactNetworkNames -ActualNetworks $unauthorizedDeclaredNetworks -ExpectedNetworks @($UnauthorizedNetwork) `
        -Service $UnauthorizedService
    Assert-ExactNetworkNames -ActualNetworks $gatewayDeclaredNetworks `
        -ExpectedNetworks @($AuthorizedNetwork, $BusinessIngressNetwork) -Service $GatewayService

    $backendId = Get-RunningContainerId $BackendService
    $gatewayId = Get-RunningContainerId $GatewayService
    $postgresId = Get-RunningContainerId $PostgresService
    $collectorId = Get-RunningContainerId $CollectorService
    $unauthorizedId = Get-RunningContainerId $UnauthorizedService
    $backendNetworks = Get-ContainerNetworks $backendId
    $gatewayNetworks = Get-ContainerNetworks $gatewayId
    $postgresNetworks = Get-ContainerNetworks $postgresId
    $collectorNetworks = Get-ContainerNetworks $collectorId
    $unauthorizedNetworks = Get-ContainerNetworks $unauthorizedId
    $actualAuthorizedNetwork = Get-ActualNetworkName $backendNetworks $AuthorizedNetwork
    $actualUnauthorizedNetwork = Get-ActualNetworkName $unauthorizedNetworks $UnauthorizedNetwork
    $actualBusinessIngressNetwork = Get-ActualNetworkName $gatewayNetworks $BusinessIngressNetwork
    Assert-ExactNetworks -Networks $backendNetworks -ExpectedNetworks @($actualAuthorizedNetwork) -Service $BackendService
    Assert-ExactNetworks -Networks $postgresNetworks -ExpectedNetworks @($actualAuthorizedNetwork) -Service $PostgresService
    Assert-ExactNetworks -Networks $collectorNetworks -ExpectedNetworks @($actualAuthorizedNetwork) -Service $CollectorService
    Assert-ExactNetworks -Networks $unauthorizedNetworks -ExpectedNetworks @($actualUnauthorizedNetwork) -Service $UnauthorizedService
    Assert-ExactNetworks -Networks $gatewayNetworks `
        -ExpectedNetworks @($actualAuthorizedNetwork, $actualBusinessIngressNetwork) -Service $GatewayService
    if ($actualAuthorizedNetwork -eq $actualUnauthorizedNetwork -or
            $actualAuthorizedNetwork -eq $actualBusinessIngressNetwork -or
            $actualUnauthorizedNetwork -eq $actualBusinessIngressNetwork) {
        Stop-WithError "Las redes efectivas autorizada, no autorizada e ingreso de negocio deben ser distintas."
    }
    Assert-InternalNetwork $actualAuthorizedNetwork
    Assert-InternalNetwork $actualUnauthorizedNetwork
    Assert-NonInternalNetwork $actualBusinessIngressNetwork
    Write-PassMessage "Las redes efectivas mantienen aislados backend, PostgreSQL, collector y unauthorized-probe."

    $backendNetworkProperty = $backendNetworks.PSObject.Properties[$actualAuthorizedNetwork].Value
    $backendIp = [string]$backendNetworkProperty.IPAddress
    if ([string]::IsNullOrWhiteSpace($backendIp)) {
        Stop-WithError "No se pudo obtener la IP dinamica del backend."
    }

    $backendPublishedPorts = Get-ContainerPublishedPorts $backendId
    $gatewayPublishedPorts = Get-ContainerPublishedPorts $gatewayId
    Assert-NoPublishedPorts $backendPublishedPorts "El backend"
    $gatewayManagementBindings = @(Get-PublishedPortBindings $gatewayPublishedPorts $ManagementContainerPort)
    if ($gatewayManagementBindings.Count -ne 0) {
        Stop-WithError "El gateway publica el puerto administrativo."
    }
    $businessHostPort = Get-ExclusivelyPublishedBusinessPort $gatewayPublishedPorts
    Write-PassMessage "docker inspect confirma que el backend no publica puertos y el gateway solo publica negocio."
    Assert-NoPublishedPortCommand @(
        "compose", "-f", $ComposeFile, "port", $BackendService, [string]$ManagementContainerPort
    ) $backendPublishedPorts $ManagementContainerPort "docker compose port del backend administrativo"
    Assert-NoPublishedPortCommand @(
        "port", $backendId, "${ManagementContainerPort}/tcp"
    ) $backendPublishedPorts $ManagementContainerPort "docker port del backend administrativo"
    Assert-NoPublishedPortCommand @(
        "compose", "-f", $ComposeFile, "port", $BackendService, [string]$BusinessContainerPort
    ) $backendPublishedPorts $BusinessContainerPort "docker compose port del backend de negocio"
    Assert-NoPublishedPortCommand @(
        "port", $backendId, "${BusinessContainerPort}/tcp"
    ) $backendPublishedPorts $BusinessContainerPort "docker port del backend de negocio"
    Assert-NoPublishedPortCommand @(
        "compose", "-f", $ComposeFile, "port", $GatewayService, [string]$ManagementContainerPort
    ) $gatewayPublishedPorts $ManagementContainerPort "docker compose port del gateway administrativo"
    Assert-NoPublishedPortCommand @(
        "port", $gatewayId, "${ManagementContainerPort}/tcp"
    ) $gatewayPublishedPorts $ManagementContainerPort "docker port del gateway administrativo"
    Assert-PublishedPortCommand @(
        "compose", "-f", $ComposeFile, "port", $GatewayService, [string]$BusinessContainerPort
    ) $businessHostPort "docker compose port del gateway de negocio"
    Assert-PublishedPortCommand @(
        "port", $gatewayId, "${BusinessContainerPort}/tcp"
    ) $businessHostPort "docker port del gateway de negocio"

    Assert-ManagementListener $backendId
    Assert-GatewayRoutingConfiguration $gatewayId

    $collectorHealthUrl = "http://${BackendService}:${ManagementContainerPort}/actuator/health"
    $collectorMetricsUrl = "http://${BackendService}:${ManagementContainerPort}/actuator/prometheus"
    Assert-HttpOkFromService $CollectorService $collectorHealthUrl
    Assert-HttpOkFromService $CollectorService $collectorMetricsUrl
    Write-PassMessage "collector-probe obtuvo health y prometheus por la red autorizada."

    foreach ($path in @("/actuator/health", "/actuator/prometheus")) {
        $managementUrlByIp = "http://${backendIp}:${ManagementContainerPort}${path}"
        Assert-NoTcpConnectionFromService $UnauthorizedService $managementUrlByIp
        Assert-NoTcpConnectionFromHost $managementUrlByIp
    }
    Write-PassMessage "unauthorized-probe y el host no alcanzan el puerto administrativo dinamico."

    foreach ($path in @("/actuator/health", "/actuator/prometheus")) {
        Assert-ActuatorDeniedOnBusinessPort ([int]$businessHostPort) $path
    }
    Assert-ProtectedBusinessRouteRejectedWithoutCredentials ([int]$businessHostPort)
    Assert-OptionalAuthorizedBusinessRoute ([int]$businessHostPort)
    Write-PassMessage "El gateway no autoriza Actuator y conserva la seguridad de negocio publicada."
}

try {
    Invoke-Validation
    Write-PassMessage "Validacion de aislamiento de red completada sin violaciones detectadas."
    exit 0
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
