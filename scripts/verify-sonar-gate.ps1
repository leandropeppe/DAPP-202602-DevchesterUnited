[CmdletBinding()]
param(
    [string]$ProjectKey = 'leandropeppe_DAPP-202602-DevchesterUnited',
    [string]$HostUrl = 'https://sonarcloud.io',
    [string]$Token = $env:SONAR_TOKEN,
    [int]$MaxOpenIssues = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw 'SONAR_TOKEN no está configurado; no se puede verificar SonarCloud.'
}

$normalizedHost = $HostUrl.TrimEnd('/')
$basicValue = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Token}:"))
$headers = @{ Authorization = "Basic $basicValue" }

function Invoke-SonarApi {
    param([Parameter(Mandatory)][string]$Uri)
    Invoke-RestMethod -Method Get -Uri $Uri -Headers $headers
}

$gateUri = "$normalizedHost/api/qualitygates/project_status?projectKey=$([Uri]::EscapeDataString($ProjectKey))"
$gate = Invoke-SonarApi -Uri $gateUri
$gateStatus = [string]$gate.projectStatus.status

$issues = [System.Collections.Generic.List[object]]::new()
$page = 1
$pageSize = 500
do {
    $issuesUri = "$normalizedHost/api/issues/search?componentKeys=$([Uri]::EscapeDataString($ProjectKey))&resolved=false&ps=$pageSize&p=$page"
    $response = Invoke-SonarApi -Uri $issuesUri
    foreach ($issue in @($response.issues)) {
        [void]$issues.Add($issue)
    }
    $total = [int]$response.total
    $page++
} while ($issues.Count -lt $total)

$openIssueCount = $issues.Count
$vulnerabilityCount = @($issues | Where-Object { $_.type -eq 'VULNERABILITY' }).Count
$criticalCodeSmellCount = @($issues | Where-Object {
    $_.type -eq 'CODE_SMELL' -and $_.severity -eq 'CRITICAL'
}).Count

Write-Output "SonarCloud Quality Gate: $gateStatus"
Write-Output "Issues abiertas: $openIssueCount (máximo permitido: $MaxOpenIssues)"
Write-Output "Vulnerabilidades abiertas: $vulnerabilityCount (máximo permitido: 0)"
Write-Output "Code smells críticos abiertos: $criticalCodeSmellCount (máximo permitido: 0)"

$failures = @()
if ($gateStatus -ne 'OK') { $failures += "Quality Gate=$gateStatus" }
if ($openIssueCount -gt $MaxOpenIssues) { $failures += "issues abiertas=$openIssueCount" }
if ($vulnerabilityCount -gt 0) { $failures += "vulnerabilidades=$vulnerabilityCount" }
if ($criticalCodeSmellCount -gt 0) { $failures += "code smells críticos=$criticalCodeSmellCount" }

if ($failures.Count -gt 0) {
    throw "SonarCloud no cumple los límites: $($failures -join ', ')."
}

Write-Output 'Verificación SonarCloud aprobada.'
