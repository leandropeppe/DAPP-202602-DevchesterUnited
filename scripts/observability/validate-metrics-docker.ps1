[CmdletBinding()]
param(
    [string]$ComposeFile = "backend/docker/observability-compose.yml",
    [int]$ManagementContainerPort = 8081,
    [int]$BusinessContainerPort = 8080,
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

function ConvertTo-WindowsProcessArgument {
    param([string]$Argument)

    if ($null -eq $Argument -or $Argument.Length -eq 0) {
        return '""'
    }
    if ($Argument -notmatch '[\s"]') {
        return $Argument
    }

    $quotedArgument = $Argument -replace '(\\*)"', '$1$1\"'
    $quotedArgument = $quotedArgument -replace '(\\+)$', '$1$1'
    return '"' + $quotedArgument + '"'
}

function Invoke-DockerWithNativeCapture {
    param([string[]]$Arguments)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = 'docker.exe'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $workingDirectory = (Get-Location).ProviderPath
    if ([string]::IsNullOrWhiteSpace($workingDirectory) -or
        -not (Test-Path -LiteralPath $workingDirectory -PathType Container)) {
        throw 'El directorio de trabajo actual no existe.'
    }
    $startInfo.WorkingDirectory = $workingDirectory
    $startInfo.Arguments = (($Arguments | ForEach-Object {
        ConvertTo-WindowsProcessArgument ([string]$_)
    }) -join ' ')

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw 'docker.exe no pudo iniciarse.'
        }

        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()

        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout   = $stdoutTask.GetAwaiter().GetResult()
            Stderr   = $stderrTask.GetAwaiter().GetResult()
        }
    } finally {
        $process.Dispose()
    }
}

function Assert-NoPublishedPort {
    param(
        [string[]]$Arguments,
        [string]$Description,
        [string]$NoPublishedPortErrorPattern
    )

    $stdout = ""
    $stderr = ""
    $exitCode = $null
    try {
        $result = Invoke-DockerWithNativeCapture $Arguments
        $exitCode = [int]$result.ExitCode
        $stdout = [string]$result.Stdout
        $stderr = [string]$result.Stderr
    } catch {
        Stop-WithError "$Description no pudo ejecutar docker de forma segura."
    }

    if (-not [string]::IsNullOrWhiteSpace($stdout)) {
        Stop-WithError "$Description confirmó una publicación del puerto administrativo."
    }

    if ($exitCode -eq 0 -and [string]::IsNullOrWhiteSpace($stderr)) {
        Write-PassMessage "$Description no muestra publicación del puerto administrativo."
        return
    }

    $composeNoPublishedPort = $false
    if ($exitCode -ne 0 -and $Arguments -contains "compose" -and $Arguments -contains "port") {
        $privatePort = [string]$Arguments[$Arguments.Count - 1]
        $expectedContainerId = [string]$script:backendId
        if ([string]::IsNullOrWhiteSpace($expectedContainerId)) {
            Stop-WithError "$Description no pudo determinar el contenedor esperado."
        }

        $expectedContainerName = @(& docker inspect --format '{{.Name}}' $expectedContainerId 2>$null)
        $inspectExitCode = $LASTEXITCODE
        if ($inspectExitCode -ne 0 -or $expectedContainerName.Count -ne 1) {
            Stop-WithError "$Description no pudo verificar el nombre del contenedor esperado."
        }

        $expectedContainerName = ([string]$expectedContainerName[0]).Trim().TrimStart('/')
        $composeNoPublishedPortPattern = "(?is)^\s*no port\s+${privatePort}/tcp\s+for container\s+$([regex]::Escape($expectedContainerName)):\s*0/tcp\s*$"
        $composeNoPublishedPort = $stderr -match $composeNoPublishedPortPattern
    }

    $documentedNoPublishedPort = $false
    if (-not [string]::IsNullOrWhiteSpace($NoPublishedPortErrorPattern)) {
        $diagnosticLines = @($stderr -split "`r?`n" | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        })
        $documentedNoPublishedPort = $diagnosticLines.Count -eq 1 -and
            $diagnosticLines[0] -match $NoPublishedPortErrorPattern -and
            $diagnosticLines[0] -match "(?i)no public port .* published for"
    }

    if ($exitCode -ne 0 -and ($composeNoPublishedPort -or $documentedNoPublishedPort)) {
        Write-PassMessage "$Description confirmó que el puerto administrativo no está publicado."
        return
    }

    $diagnostic = if ([string]::IsNullOrWhiteSpace($stderr)) {
        "sin diagnóstico stderr"
    } else {
        "diagnóstico stderr no reconocido"
    }
    Stop-WithError "$Description produjo una respuesta ambigua (código $exitCode; $diagnostic)."
}

function Invoke-CollectorRequest {
    param([string[]]$Arguments)

    $null = & docker compose -f $ComposeFile run --rm --no-deps $CollectorService curl `
        --silent --show-error --output /dev/null `
        --connect-timeout 3 --max-time 5 @Arguments 2>$null
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Stop-WithError "collector-probe no pudo generar el tráfico HTTP controlado."
    }
}

function Invoke-CollectorTraffic {
    param([string]$BaseUrl)

    $queryValue = "metrics-query-sensitive-7f31"
    $authorizationValue = "Bearer-metrics-sensitive-91a2"
    $apiKeyValue = "metrics-api-key-sensitive-4c83"
    $cookieValue = "metrics-cookie-sensitive-b812"
    $tokenValue = "metrics-token-sensitive-5d64"
    $payloadValue = "metrics-payload-sensitive-a927"
    $variablePathValue = "00000000-0000-0000-0000-000000000017"

    Invoke-CollectorRequest @(
        "--get",
        "--data-urlencode", "search=$queryValue",
        "-H", "Authorization: $authorizationValue",
        "-H", "X-API-Key: $apiKeyValue",
        "-H", "Cookie: session=$cookieValue",
        "-H", "X-Request-Token: $tokenValue",
        "$BaseUrl/api/v1/players?metrics-contract=controlled"
    )
    Invoke-CollectorRequest @(
        "--request", "POST",
        "-H", "Content-Type: application/json",
        "-H", "Authorization: $authorizationValue",
        "--data", "{`"username`":`"$payloadValue`",`"password`":`"$tokenValue`"}",
        "$BaseUrl/api/v1/auth/login"
    )
    Invoke-CollectorRequest @(
        "-H", "Authorization: $authorizationValue",
        "-H", "X-API-Key: $apiKeyValue",
        "-H", "Cookie: session=$cookieValue",
        "$BaseUrl/api/v1/players/$variablePathValue"
    )
}

function Invoke-CollectorMetrics {
    param([string]$Url)

    $statusMarker = "__METRICS_STATUS__"
    $contentTypeMarker = "__METRICS_CONTENT_TYPE__"
    $result = @(& docker compose -f $ComposeFile run --rm --no-deps $CollectorService curl `
        --silent --show-error --output - --write-out "`n${statusMarker}:%{http_code}`n${contentTypeMarker}:%{content_type}" `
        --connect-timeout 3 --max-time 5 $Url 2>$null)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Stop-WithError "collector-probe no pudo completar la solicitud HTTP de metrics."
    }

    $output = $result -join "`n"
    $match = [regex]::Match($output,
        "(?s)^(?<body>.*)\r?\n${statusMarker}:(?<status>\d{3})\r?\n${contentTypeMarker}:(?<contentType>.*)$")
    if (-not $match.Success) {
        Stop-WithError "No se pudo separar el cuerpo, estado HTTP y Content-Type de metrics."
    }

    $statusCode = [int]$match.Groups["status"].Value
    $contentType = $match.Groups["contentType"].Value.Trim()
    $body = $match.Groups["body"].Value.Trim()
    if ($statusCode -ne 200) {
        Stop-WithError "collector-probe recibió HTTP $statusCode en metrics."
    }
    if ($contentType -notmatch '(?i)^(?:text/plain|application/openmetrics-text)(?:;|$)') {
        Stop-WithError "metrics devolvió un Content-Type no compatible con Prometheus/OpenMetrics."
    }
    if ([string]::IsNullOrWhiteSpace($body)) {
        Stop-WithError "metrics devolvió un cuerpo vacío."
    }

    return [pscustomobject]@{
        ContentType = $contentType
        Body = $body
    }
}

function Assert-MetricFamily {
    param(
        [string]$Metrics,
        [string]$MetricName
    )

    if ($Metrics -notmatch "(?m)^$([regex]::Escape($MetricName))(?:\{|\s|_)") {
        Stop-WithError "No se encontró la familia de métricas esperada $MetricName."
    }
}

function Assert-MetricsDoNotContainSensitiveValues {
    param([string]$Metrics)

    $sensitiveValues = @(
        "metrics-query-sensitive-7f31",
        "Bearer-metrics-sensitive-91a2",
        "metrics-api-key-sensitive-4c83",
        "metrics-cookie-sensitive-b812",
        "metrics-token-sensitive-5d64",
        "metrics-payload-sensitive-a927",
        "00000000-0000-0000-0000-000000000017"
    )
    foreach ($sensitiveValue in $sensitiveValues) {
        if ($Metrics.Contains($sensitiveValue)) {
            Stop-WithError "metrics expone un valor sensible o de alta cardinalidad."
        }
    }

    $httpSamples = $Metrics -split "`r?`n" | Where-Object { $_ -match '^http_server_requests' }
    foreach ($sample in $httpSamples) {
        if ($sample.Contains("?")) {
            Stop-WithError "metrics HTTP expone una query string."
        }
        if ($sample -match '(?i)(?:query|authorization|api[_-]?key|cookie|token|payload|password|session|email|user[_-]?(?:id|name)?|request[_-]?id)\s*=') {
            Stop-WithError "metrics HTTP expone una etiqueta sensible o de alta cardinalidad."
        }
    }
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
        Stop-WithError "$Service no confirmó la ejecución de curl; se considera un fallo de Docker Compose o infraestructura."
    }
    if ($exitCode -eq 0) {
        Stop-WithError "$Service estableció una conexión TCP con el puerto administrativo."
    }
    if ($exitCode -notin @(7, 28)) {
        Stop-WithError "$Service produjo un error de infraestructura no concluyente (curl $exitCode)."
    }

    Write-PassMessage "$Service no pudo obtener las métricas administrativas."
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

    Write-PassMessage "El host no pudo obtener las métricas administrativas."
}

if (-not (Test-Path -LiteralPath $ComposeFile)) {
    Stop-WithError "No existe el archivo Compose $ComposeFile."
}
if ($ManagementContainerPort -le 0) {
    Stop-WithError "El puerto administrativo configurado debe ser positivo."
}
if ($BusinessContainerPort -le 0) {
    Stop-WithError "El puerto de negocio configurado debe ser positivo."
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
if ($backendDeclaredNetworks.Count -ne 1 -or $collectorDeclaredNetworks.Count -ne 1) {
    Stop-WithError "Backend y collector-probe deben permanecer únicamente en la red autorizada."
}
Write-PassMessage "La topología declarada separa la red autorizada de unauthorized-probe."

$backendId = Get-RunningContainerId $BackendService
$backendNetworks = Get-ContainerNetworks $backendId
$actualAuthorizedNetwork = Get-ActualAuthorizedNetworkName $backendNetworks $AuthorizedNetwork
if (@($backendNetworks.PSObject.Properties.Name).Count -ne 1) {
    Stop-WithError "El backend está conectado a una red adicional no autorizada."
}
$backendNetworkProperty = Get-NetworkProperty $backendNetworks $actualAuthorizedNetwork
$backendIp = [string]$backendNetworkProperty.IPAddress
if ([string]::IsNullOrWhiteSpace($backendIp)) {
    Stop-WithError "No se pudo obtener dinámicamente la IP del backend en la red autorizada."
}
Write-PassMessage "El backend está conectado únicamente a la red autorizada efectiva."

$collectorId = (& docker compose -f $ComposeFile ps --status running -q $CollectorService 2>$null).Trim()
if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($collectorId)) {
    $collectorNetworks = Get-ContainerNetworks $collectorId
    $null = Get-NetworkProperty $collectorNetworks $actualAuthorizedNetwork
    if (@($collectorNetworks.PSObject.Properties.Name).Count -ne 1) {
        Stop-WithError "collector-probe está conectado a una red adicional no autorizada."
    }
    Write-PassMessage "collector-probe está conectado únicamente a la red autorizada efectiva."
} else {
    Write-Host "PASS: collector-probe será creado temporalmente por las comprobaciones HTTP." -ForegroundColor Green
}

$publishedPorts = Get-ContainerPublishedPorts $backendId
$managementBindings = Get-PublishedPortBindings $publishedPorts $ManagementContainerPort
if ($managementBindings.Count -gt 0) {
    Stop-WithError "El puerto administrativo está publicado al host."
}
Write-PassMessage "El puerto administrativo no está publicado al host."
Assert-NoPublishedPort @(
    "compose", "-f", $ComposeFile, "port", $BackendService, [string]$ManagementContainerPort
) "docker compose port" ""
Assert-NoPublishedPort @(
    "port", $backendId, "${ManagementContainerPort}/tcp"
) "docker port" "(?i)no public port .* published"

$collectorBaseUrl = "http://${BackendService}:${BusinessContainerPort}"
Invoke-CollectorTraffic $collectorBaseUrl
Write-PassMessage "collector-probe generó tráfico HTTP controlado."

$collectorMetricsUrl = "http://${BackendService}:${ManagementContainerPort}/actuator/prometheus"
$metricsResponse = Invoke-CollectorMetrics $collectorMetricsUrl
Assert-MetricFamily $metricsResponse.Body "jvm_memory_used_bytes"
Assert-MetricFamily $metricsResponse.Body "jvm_buffer_memory_used_bytes"
Assert-MetricFamily $metricsResponse.Body "http_server_requests_seconds"
Assert-MetricsDoNotContainSensitiveValues $metricsResponse.Body
Write-PassMessage "collector-probe obtuvo metrics HTTP 200, formato compatible y familias JVM, memoria y HTTP."

$managementUrlByIp = "http://${backendIp}:${ManagementContainerPort}/actuator/prometheus"
Assert-NoTcpConnectionFromCompose $UnauthorizedService $managementUrlByIp
Assert-NoTcpConnectionFromHost $managementUrlByIp

Write-PassMessage "Validación de metrics Docker completada sin violaciones detectadas."
