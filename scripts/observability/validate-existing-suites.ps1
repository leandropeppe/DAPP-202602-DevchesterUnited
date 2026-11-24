[CmdletBinding()]
param(
    [string]$FrontendDirectory = "frontend",
    [string]$FrontendE2EComposeFile = "frontend/e2e/compose.yml",
    [string]$PerformanceComposeFile = "performance/compose.yml",
    [int]$PerformanceStartupTimeoutSeconds = 60,
    [int]$FlywayMigrationTimeoutSeconds = 120,
    [ValidateSet("all", "performance")]
    [string]$ValidationMode = "all"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$frontendPath = Join-Path $repositoryRoot $FrontendDirectory
$frontendComposePath = Join-Path $repositoryRoot $FrontendE2EComposeFile
$performanceComposePath = Join-Path $repositoryRoot $PerformanceComposeFile
$backendJarPath = Join-Path $repositoryRoot "backend/build/libs/DevchesterUnitedV2-0.0.1-SNAPSHOT.jar"
$performanceSeedPath = Join-Path $repositoryRoot "performance/seed.sql"
$performanceProjectName = "obs-us4-performance-" + [Guid]::NewGuid().ToString("N")
$performancePort = 15433
$cleanupTimeoutSeconds = 20
$jdbcProbeTimeoutSeconds = 20
$nativeOutputDrainTimeoutSeconds = 10
$temporaryPsqlTimeoutSeconds = 60
$script:temporaryContainerId = $null
$script:temporaryJdbcProbeDiagnostic = $null
$script:temporaryProjectCreated = $false

function Write-PassMessage {
    param([string]$Message)
    Write-Host "PASS: $Message" -ForegroundColor Green
}

function Stop-WithError {
    param([string]$Message)
    throw "FAIL: $Message"
}

function Invoke-Native {
    param([string]$FilePath, [string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $output = @()
    $exitCode = -1
    try {
        $ErrorActionPreference = "Continue"
        if ($null -ne (Get-Command $FilePath -ErrorAction SilentlyContinue)) {
            try {
                $output = @(& $FilePath @Arguments 2>&1)
                $exitCode = $LASTEXITCODE
            } catch {
                $exitCode = -1
            }
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = ($output -join [Environment]::NewLine) }
}

function ConvertTo-WindowsCommandLineArgument {
    param([AllowEmptyString()][string]$Argument)
    if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') { return $Argument }
    $escaped = [regex]::Replace($Argument, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Get-NativeProcessIdentity {
    param([object]$ProcessRecord)
    if ($null -eq $ProcessRecord -or $null -eq $ProcessRecord.ProcessId -or
        [string]::IsNullOrWhiteSpace([string]$ProcessRecord.CreationDate)) {
        return $null
    }
    $creationDate = $ProcessRecord.CreationDate
    if ($creationDate -is [datetime]) {
        $creationTicks = $creationDate.ToUniversalTime().Ticks
    } elseif ($creationDate -is [string] -and $creationDate -match '^\d{14}\.\d{6}[+-]\d{3}$') {
        try {
            $creationTicks = [System.Management.ManagementDateTimeConverter]::ToDateTime(
                $creationDate).ToUniversalTime().Ticks
        } catch {
            return $null
        }
    } else {
        return $null
    }
    return [pscustomobject]@{
        ProcessId = [int]$ProcessRecord.ProcessId
        ProcessName = [string]$ProcessRecord.Name
        CreationTicks = $creationTicks
    }
}

function Stop-NativeProcessTree {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process -or $Process.HasExited) { return $true }
    $taskkill = $null
    try {
        $rootId = $Process.Id
        $rootName = $Process.ProcessName
        $rootStartTicks = $Process.StartTime.ToUniversalTime().Ticks
        $processRecords = @(Get-CimInstance Win32_Process -ErrorAction Stop)
        $rootRecord = @($processRecords | Where-Object { [int]$_.ProcessId -eq $rootId })
        if ($rootRecord.Count -ne 1) { return $false }
        $rootIdentity = Get-NativeProcessIdentity $rootRecord[0]
        if ($null -eq $rootIdentity) { return $false }
        $rootProcessName = [System.IO.Path]::GetFileNameWithoutExtension($rootName)
        $rootIdentityName = [System.IO.Path]::GetFileNameWithoutExtension($rootIdentity.ProcessName)
        $rootStartMicrosecondTicks = $rootStartTicks - ($rootStartTicks % 10)
        $rootCreationMicrosecondTicks = $rootIdentity.CreationTicks - ($rootIdentity.CreationTicks % 10)
        if ($rootIdentity.ProcessId -ne $rootId -or $rootIdentityName -ine $rootProcessName -or
            $rootCreationMicrosecondTicks -ne $rootStartMicrosecondTicks) { return $false }

        $childrenByParent = @{}
        foreach ($record in $processRecords) {
            $parentId = [int]$record.ParentProcessId
            if (-not $childrenByParent.ContainsKey($parentId)) {
                $childrenByParent[$parentId] = New-Object System.Collections.ArrayList
            }
            [void]$childrenByParent[$parentId].Add($record)
        }
        $pendingParents = New-Object System.Collections.Queue
        $pendingParents.Enqueue($rootId)
        $treeIdentities = New-Object System.Collections.ArrayList
        while ($pendingParents.Count -gt 0) {
            $parentId = $pendingParents.Dequeue()
            if (-not $childrenByParent.ContainsKey($parentId)) { continue }
            foreach ($childRecord in $childrenByParent[$parentId]) {
                $childIdentity = Get-NativeProcessIdentity $childRecord
                if ($null -eq $childIdentity) { return $false }
                [void]$treeIdentities.Add($childIdentity)
                $pendingParents.Enqueue($childIdentity.ProcessId)
            }
        }

        $taskkillCommand = Get-Command "taskkill.exe" -ErrorAction SilentlyContinue
        if ($null -eq $taskkillCommand) { return $false }
        $taskkill = New-Object System.Diagnostics.Process
        $taskkill.StartInfo = New-Object System.Diagnostics.ProcessStartInfo
        $taskkill.StartInfo.FileName = $taskkillCommand.Source
        $taskkill.StartInfo.Arguments = "/PID $rootId /T /F"
        $taskkill.StartInfo.UseShellExecute = $false
        $taskkill.StartInfo.CreateNoWindow = $true
        $taskkill.StartInfo.RedirectStandardOutput = $true
        $taskkill.StartInfo.RedirectStandardError = $true
        if (-not $taskkill.Start()) { return $false }
        $standardOutputTask = $taskkill.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $taskkill.StandardError.ReadToEndAsync()
        if (-not $taskkill.WaitForExit($cleanupTimeoutSeconds * 1000)) { return $false }
        $tasks = [System.Threading.Tasks.Task[]]@($standardOutputTask, $standardErrorTask)
        if (-not [System.Threading.Tasks.Task]::WaitAll($tasks, $nativeOutputDrainTimeoutSeconds * 1000)) { return $false }

        $deadline = (Get-Date).AddSeconds($cleanupTimeoutSeconds)
        do {
            $remaining = @($rootIdentity) + @($treeIdentities)
            $identityStillRunning = $false
            foreach ($identity in $remaining) {
                $currentRecords = @(Get-CimInstance Win32_Process -Filter "ProcessId = $($identity.ProcessId)" -ErrorAction Stop)
                foreach ($currentRecord in $currentRecords) {
                    $currentIdentity = Get-NativeProcessIdentity $currentRecord
                    if ($null -eq $currentIdentity) {
                        $identityStillRunning = $true
                        break
                    }
                    if ($currentIdentity.CreationTicks -eq $identity.CreationTicks -and
                        $currentIdentity.ProcessName -eq $identity.ProcessName) {
                        $identityStillRunning = $true
                        break
                    }
                }
                if ($identityStillRunning) { break }
            }
            if (-not $identityStillRunning) { return $true }
            Start-Sleep -Milliseconds 100
        } while ((Get-Date) -lt $deadline)
        return $false
    } catch {
        return $false
    } finally {
        if ($null -ne $taskkill) {
            if (-not $taskkill.HasExited) { Stop-Process -Id $taskkill.Id -Force -ErrorAction SilentlyContinue }
            $taskkill.Dispose()
        }
    }
}

function Invoke-NativeWithTimeout {
    param([string]$FilePath, [string[]]$Arguments, [int]$TimeoutSeconds, [string]$Operation,
        [hashtable]$Environment = @{})
    $command = Get-Command $FilePath -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        Stop-WithError "Falta la herramienta requerida: $FilePath."
    }
    $process = New-Object System.Diagnostics.Process
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $command.Source
    $startInfo.Arguments = (($Arguments | ForEach-Object { ConvertTo-WindowsCommandLineArgument $_ }) -join ' ')
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($name in $Environment.Keys) {
        $startInfo.EnvironmentVariables[$name] = [string]$Environment[$name]
    }
    $process.StartInfo = $startInfo
    $processStarted = $false
    $terminationConfirmed = $true
    try {
        $processStarted = $process.Start()
        if (-not $processStarted) {
            Stop-WithError "No se pudo iniciar la operacion temporal $Operation [phase=performance-smoke; category=native-command-start; exitCode=n/a]."
        }
        $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $terminationConfirmed = Stop-NativeProcessTree $process
            if (-not $terminationConfirmed) {
                Stop-WithError "La operacion temporal $Operation no termino despues de forzar su detencion [phase=cleanup; category=native-process-cleanup; exitCode=n/a]."
            }
            Stop-WithError "La operacion temporal $Operation excedio el timeout [phase=performance-smoke; category=native-command-timeout; exitCode=n/a]."
        }
        $tasks = [System.Threading.Tasks.Task[]]@($standardOutputTask, $standardErrorTask)
        if (-not [System.Threading.Tasks.Task]::WaitAll($tasks, $nativeOutputDrainTimeoutSeconds * 1000)) {
            Stop-WithError "La captura de la operacion temporal $Operation excedio el timeout [phase=performance-smoke; category=native-output-timeout; exitCode=n/a]."
        }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $standardOutputTask.GetAwaiter().GetResult() + [Environment]::NewLine + $standardErrorTask.GetAwaiter().GetResult() }
    } catch {
        if ($_.Exception.Message -match "category=") { throw }
        Stop-WithError "La operacion temporal $Operation no pudo completar la captura [phase=performance-smoke; category=native-output-failure; exitCode=n/a]."
    } finally {
        if ($processStarted -and -not $process.HasExited) {
            $terminationConfirmed = Stop-NativeProcessTree $process
        }
        foreach ($name in $Environment.Keys) {
            $startInfo.EnvironmentVariables.Remove($name)
        }
        $process.Dispose()
        if (-not $terminationConfirmed) {
            Stop-WithError "La operacion temporal $Operation dejo un proceso sin confirmar su detencion [phase=cleanup; category=native-process-cleanup; exitCode=n/a]."
        }
    }
}

function Invoke-Docker {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 0, [string]$Operation = "docker")
    if ($TimeoutSeconds -le 0) { return Invoke-Native "docker.exe" $Arguments }
    return Invoke-NativeWithTimeout -FilePath "docker.exe" -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds -Operation $Operation
}

function Invoke-PerformanceCompose {
    param([string[]]$Arguments)
    return Invoke-Docker (@("compose", "--project-name", $performanceProjectName,
            "-f", $performanceComposePath) + $Arguments)
}

function Assert-CommandAvailable {
    param([string]$CommandName)
    if ($null -eq (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        Stop-WithError "Falta la herramienta requerida: $CommandName."
    }
}

function Assert-RequiredFile {
    param([string]$Path, [string]$Description)
    if (-not (Test-Path -LiteralPath $Path)) {
        Stop-WithError "Falta el prerrequisito requerido: $Description."
    }
}

function Assert-DockerAvailable {
    $result = Invoke-Docker @("version", "--format", "{{.Server.Version}}")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "docker.exe no esta disponible o el daemon no responde [phase=preflight; category=docker-daemon; exitCode=$($result.ExitCode)]."
    }
}

function Assert-ComposeFile {
    param([string]$Path)
    $result = Invoke-Docker @("compose", "-f", $Path, "config", "-q")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "El preflight del Compose requerido fallo [phase=preflight; category=compose-config; exitCode=$($result.ExitCode)]."
    }
}

function Assert-FrontendE2EProjectIdle {
    $result = Invoke-Docker @("compose", "--project-name", "devchester-e2e", "-f", $frontendComposePath,
        "ps", "--all", "--quiet")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo inspeccionar el proyecto aislado de Playwright [phase=preflight; category=e2e-project-inspection; exitCode=$($result.ExitCode)]."
    }
    if (-not [string]::IsNullOrWhiteSpace($result.Output)) {
        Stop-WithError "El proyecto aislado devchester-e2e ya tiene recursos; no se los modificara."
    }
}

function Assert-PerformancePortAvailable {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $performancePort -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        Stop-WithError "El puerto local de performance $performancePort ya esta ocupado; no se iniciara un proyecto temporal."
    }
}

function Assert-TemporaryProjectUnused {
    $result = Invoke-Docker @("ps", "--all", "--quiet", "--filter",
        "label=com.docker.compose.project=$performanceProjectName")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo comprobar la exclusividad del proyecto temporal [phase=performance-smoke; category=project-inspection; exitCode=$($result.ExitCode)]."
    }
    if (-not [string]::IsNullOrWhiteSpace($result.Output)) {
        Stop-WithError "El identificador de proyecto temporal ya tiene recursos; se rechaza reutilizarlos."
    }
}

function Invoke-FrontendE2E {
    Push-Location $frontendPath
    try {
        # Comandos oficiales de Playwright; se conservan sin cambios.
        $verify = Invoke-Native "npm.cmd" @("run", "verify:frontend")
        if ($verify.ExitCode -ne 0) { Stop-WithError "La verificacion frontend fallo [phase=frontend-verify; category=npm; exitCode=$($verify.ExitCode)]." }
        $build = Invoke-Native "npm.cmd" @("run", "build")
        if ($build.ExitCode -ne 0) { Stop-WithError "La construccion frontend fallo [phase=frontend-build; category=npm; exitCode=$($build.ExitCode)]." }
        $playwright = Invoke-Native "npm.cmd" @("run", "test:e2e")
        if ($playwright.ExitCode -ne 0) { Stop-WithError "La suite Playwright fallo [phase=frontend-e2e; category=npm; exitCode=$($playwright.ExitCode)]." }
    } finally {
        Pop-Location
    }
    Write-PassMessage "Las suites Playwright existentes finalizaron correctamente."
}

function Get-TemporaryContainerId {
    $result = Invoke-PerformanceCompose @("ps", "--quiet", "postgres")
    $containerIds = @($result.Output -split "`r?`n" | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        } | ForEach-Object { $_.Trim() })
    if ($result.ExitCode -ne 0 -or $containerIds.Count -ne 1) {
        Stop-WithError "No se encontro exactamente un PostgreSQL temporal [phase=performance-smoke; category=compose-ps; exitCode=$($result.ExitCode)]."
    }
    return $containerIds[0]
}

function Assert-TemporaryPostgresIdentity {
    param([string]$Operation)
    if ([string]::IsNullOrWhiteSpace($script:temporaryContainerId)) {
        Stop-WithError "No hay identificador de PostgreSQL temporal para $Operation."
    }
    $currentContainerId = Get-TemporaryContainerId
    if ($currentContainerId -ne $script:temporaryContainerId) {
        Stop-WithError "La identidad del contenedor temporal no coincide; se aborta $Operation."
    }
    $inspection = Invoke-Docker @("inspect", "--format",
        '{{.Id}}|{{index .Config.Labels \"com.docker.compose.project\"}}|{{index .Config.Labels \"com.docker.compose.service\"}}|{{.State.Running}}',
        $script:temporaryContainerId)
    if ($inspection.ExitCode -ne 0) {
        Stop-WithError "No se pudo verificar la identidad del contenedor temporal [phase=performance-smoke; category=container-inspection; exitCode=$($inspection.ExitCode)]."
    }
    $identity = $inspection.Output.Trim().Split('|')
    if ($identity.Count -ne 4 -or $identity[0] -ne $script:temporaryContainerId -or
        $identity[1] -ne $performanceProjectName -or $identity[2] -ne "postgres" -or
        $identity[3] -ne "true") {
        Stop-WithError "El contenedor no pertenece al proyecto temporal PostgreSQL esperado; se aborta $Operation."
    }
    $publishedPort = Invoke-Docker @("port", $script:temporaryContainerId, "5432/tcp")
    if ($publishedPort.ExitCode -ne 0 -or $publishedPort.Output.Trim() -ne "127.0.0.1:$performancePort") {
        Stop-WithError "El puerto publicado no corresponde al contenedor temporal esperado; se aborta $Operation."
    }
    $databaseCheck = Invoke-Docker -Arguments @("exec", $script:temporaryContainerId, "psql", "-X", "-v",
        "ON_ERROR_STOP=1", "-tAc",
        "SELECT current_database() = 'devchester_performance' AND current_user = 'performance_user'",
        "-U", "performance_user", "-d", "devchester_performance") -TimeoutSeconds $temporaryPsqlTimeoutSeconds -Operation "psql-identity"
    if ($databaseCheck.ExitCode -ne 0 -or $databaseCheck.Output.Trim() -ne "t") {
        Stop-WithError "La conexion no confirma la base temporal esperada; se aborta $Operation."
    }
}

function Wait-ForTemporaryPostgres {
    $deadline = (Get-Date).AddSeconds($PerformanceStartupTimeoutSeconds)
    do {
        $result = Invoke-Docker @("inspect", "--format",
            "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}", $script:temporaryContainerId)
        if ($result.ExitCode -ne 0) {
            Stop-WithError "No se pudo inspeccionar PostgreSQL temporal [phase=performance-smoke; category=docker-inspect; exitCode=$($result.ExitCode)]."
        }
        if ($result.Output.Trim() -eq "running|healthy") { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Stop-WithError "PostgreSQL temporal no alcanzo estado healthy [phase=performance-smoke; category=startup-timeout; exitCode=n/a]."
}

function Assert-TemporaryPostgresWindowsConnectivity {
    Assert-TemporaryPostgresIdentity "la conectividad TCP desde Windows"
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    try {
        $connectTask = $tcpClient.ConnectAsync("127.0.0.1", $performancePort)
        if (-not $connectTask.Wait(5000)) {
            Stop-WithError "PostgreSQL temporal no acepta conexiones TCP desde Windows [phase=performance-smoke; category=windows-tcp-connectivity; exitCode=n/a]."
        }
        $null = $connectTask.GetAwaiter().GetResult()
    } catch {
        Stop-WithError "PostgreSQL temporal rechazo la conexion TCP desde Windows [phase=performance-smoke; category=windows-tcp-connectivity; exitCode=n/a]."
    } finally {
        $tcpClient.Dispose()
    }
}

function Get-TemporaryDatabasePassword {
    Assert-TemporaryPostgresIdentity "la configuracion temporal de Flyway"
    $result = Invoke-Docker @("inspect", "--format", '{{range .Config.Env}}{{println .}}{{end}}', $script:temporaryContainerId)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo obtener la configuracion del contenedor temporal [phase=performance-smoke; category=temporary-db-config; exitCode=$($result.ExitCode)]."
    }
    $passwordLine = @($result.Output -split "`r?`n" | Where-Object { $_ -like "POSTGRES_PASSWORD=*" })
    if ($passwordLine.Count -ne 1 -or $passwordLine[0].Length -le "POSTGRES_PASSWORD=".Length) {
        Stop-WithError "La configuracion de PostgreSQL temporal es incompleta; no se iniciara Flyway."
    }
    return $passwordLine[0].Substring("POSTGRES_PASSWORD=".Length)
}

function Get-TemporaryJdbcUrl {
    return "jdbc:postgresql://127.0.0.1:$performancePort/devchester_performance"
}

function New-TemporaryJdbcProbe {
    $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("obs-us4-jdbc-" + [Guid]::NewGuid().ToString("N"))
    [System.IO.Directory]::CreateDirectory($temporaryDirectory) | Out-Null
    try {
    $driverPath = Join-Path $temporaryDirectory "postgresql-driver.jar"
    $sourcePath = Join-Path $temporaryDirectory "TemporaryJdbcProbe.java"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($backendJarPath)
    try {
        $driverEntries = @($archive.Entries | Where-Object { $_.FullName -match "^BOOT-INF/lib/postgresql-[^/]+\.jar$" })
        if ($driverEntries.Count -ne 1) {
            Stop-WithError "No se encontro exactamente un driver PostgreSQL en el bootJar [phase=performance-smoke; category=jdbc-probe-driver; exitCode=n/a]."
        }
        $entryStream = $driverEntries[0].Open()
        $driverStream = [System.IO.File]::Open($driverPath, [System.IO.FileMode]::CreateNew)
        try {
            $entryStream.CopyTo($driverStream)
        } finally {
            $driverStream.Dispose()
            $entryStream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
    $source = @'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TimeZone;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

public final class TemporaryJdbcProbe {
    private static final String PASSWORD_ENV = "TEMPORARY_JDBC_PROBE_PASSWORD";
    private static final String[][] SAFE_SERVER_PARAMETERS = {
            {"TimeZone", "TimeZone"},
            {"DateStyle", "DateStyle"},
            {"client_encoding", "client-encoding"}
    };

    private static String safeSqlState(SQLException exception) {
        String state = exception.getSQLState();
        return state != null && state.matches("[0-9A-Z]{5}") ? state : "not-available";
    }

    private static String safeException(SQLException exception) {
        return exception instanceof PSQLException ? "PSQLException" : "SQLException";
    }

    private static String safeServerParameter(SQLException exception) {
        PSQLException postgresException = exception instanceof PSQLException ? (PSQLException) exception : null;
        ServerErrorMessage serverError = postgresException == null ? null : postgresException.getServerErrorMessage();
        String message = serverError == null ? null : serverError.getMessage();
        if (message == null) {
            return "not-available";
        }
        for (String[] parameter : SAFE_SERVER_PARAMETERS) {
            if (message.contains(parameter[0])) {
                return parameter[1];
            }
        }
        return "not-classified";
    }

    private static String safeJavaTimezoneKind() {
        String timezone = TimeZone.getDefault().getID();
        if ("UTC".equals(timezone)) {
            return "utc";
        }
        return timezone != null && timezone.matches("[A-Za-z]+(?:/[A-Za-z_+-]+)+") ? "iana" : "non-iana";
    }

    private static void failure(String stage, SQLException exception) {
        System.out.println("JDBC_PROBE_FAIL; stage=" + stage + "; exception=" + safeException(exception)
                + "; sqlstate=" + safeSqlState(exception) + "; vendor-code=" + exception.getErrorCode()
                + "; server-parameter=" + safeServerParameter(exception)
                + "; java-timezone=" + safeJavaTimezoneKind());
    }

    private static void failure(String stage) {
        System.out.println("JDBC_PROBE_FAIL; stage=" + stage
                + "; exception=destination-mismatch; sqlstate=not-available; vendor-code=not-available"
                + "; server-parameter=not-available; java-timezone=" + safeJavaTimezoneKind());
    }

    public static void main(String[] args) {
        DriverManager.setLoginTimeout(5);
        Connection connection;
        try {
            connection = DriverManager.getConnection(args[0], args[1], System.getenv(PASSWORD_ENV));
        } catch (SQLException exception) {
            failure("connection", exception);
            System.exit(2);
            return;
        }
        try (Connection closeableConnection = connection; Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT 1, current_database(), current_user")) {
                if (!result.next() || result.getInt(1) != 1
                        || !"devchester_performance".equals(result.getString(2))
                        || !"performance_user".equals(result.getString(3))) {
                    failure("query");
                    System.exit(2);
                }
            }
            System.out.println("JDBC_PROBE_PASS");
        } catch (SQLException exception) {
            failure("query", exception);
            System.exit(2);
        } catch (Exception exception) {
            System.out.println("JDBC_PROBE_FAIL; stage=probe; exception=probe-execution; sqlstate=not-available; vendor-code=not-available"
                    + "; server-parameter=not-available; java-timezone=" + safeJavaTimezoneKind());
            System.exit(3);
        }
    }
}
'@
    [System.IO.File]::WriteAllText($sourcePath, $source, (New-Object System.Text.UTF8Encoding($false)))
    return [pscustomobject]@{ Directory = $temporaryDirectory; DriverPath = $driverPath; SourcePath = $sourcePath }
    } catch {
        if ([System.IO.Directory]::Exists($temporaryDirectory)) { [System.IO.Directory]::Delete($temporaryDirectory, $true) }
        throw
    }
}

function Remove-TemporaryJdbcProbe {
    param([string]$Directory)
    if ([string]::IsNullOrWhiteSpace($Directory) -or -not [System.IO.Directory]::Exists($Directory)) { return }
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $fullDirectory = [System.IO.Path]::GetFullPath($Directory).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $isExpectedDirectory = [System.IO.Path]::GetDirectoryName($fullDirectory) -eq $temporaryRoot -and [System.IO.Path]::GetFileName($fullDirectory) -like "obs-us4-jdbc-*"
    if (-not $isExpectedDirectory) {
        Stop-WithError "Se rechazo la limpieza fuera del directorio temporal de la sonda JDBC [phase=cleanup; category=jdbc-probe-cleanup; exitCode=n/a]."
    }
    [System.IO.Directory]::Delete($fullDirectory, $true)
}

function Invoke-TemporaryJdbcProbe {
    Assert-TemporaryPostgresIdentity "la sonda JDBC temporal"
    $temporaryDatabasePassword = Get-TemporaryDatabasePassword
    $probe = $null
    $process = $null
    $startInfo = $null
    $script:temporaryJdbcProbeDiagnostic = $null
    try {
        $probe = New-TemporaryJdbcProbe
        $javaCommand = Get-Command "java.exe" -ErrorAction Stop
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = $javaCommand.Source
        $startInfo.Arguments = '-Duser.timezone=UTC --class-path "' + $probe.DriverPath + '" "' + $probe.SourcePath + '" "' + (Get-TemporaryJdbcUrl) + '" "performance_user"'
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.EnvironmentVariables["TEMPORARY_JDBC_PROBE_PASSWORD"] = $temporaryDatabasePassword
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            Stop-WithError "No se pudo iniciar la sonda JDBC temporal [phase=performance-smoke; category=jdbc-direct-probe; exitCode=n/a]."
        }
        $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($jdbcProbeTimeoutSeconds * 1000)) {
            Stop-Process -Id $process.Id -Force -ErrorAction Stop
            Stop-WithError "La sonda JDBC temporal excedio el timeout [phase=performance-smoke; category=jdbc-direct-probe-timeout; exitCode=n/a]."
        }
        $standardOutput = $standardOutputTask.GetAwaiter().GetResult().Trim()
        $null = $standardErrorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -eq 0 -and $standardOutput -eq "JDBC_PROBE_PASS") {
            Write-PassMessage "La sonda JDBC temporal confirmo SELECT 1, base y usuario esperados."
            return
        }
        $probeFailure = [regex]::Match($standardOutput, "^JDBC_PROBE_FAIL; stage=(?<stage>connection|query|probe); exception=(?<exception>[A-Za-z-]+); sqlstate=(?<sqlState>[0-9A-Z-]+); vendor-code=(?<vendorCode>-?\d+|not-available); server-parameter=(?<serverParameter>TimeZone|DateStyle|client-encoding|not-classified|not-available); java-timezone=(?<javaTimezone>utc|iana|non-iana)$")
        if ($probeFailure.Success) {
            $script:temporaryJdbcProbeDiagnostic = "jdbc-probe=direct-failure; stage=$($probeFailure.Groups["stage"].Value); exception=$($probeFailure.Groups["exception"].Value); sqlstate=$($probeFailure.Groups["sqlState"].Value); vendor-code=$($probeFailure.Groups["vendorCode"].Value); server-parameter=$($probeFailure.Groups["serverParameter"].Value); java-timezone=$($probeFailure.Groups["javaTimezone"].Value)"
        } else {
            $script:temporaryJdbcProbeDiagnostic = "jdbc-probe=unclassified; stage=probe; exception=probe-execution; sqlstate=not-available; vendor-code=not-available; server-parameter=not-available; java-timezone=not-available"
        }
        Stop-WithError "La sonda JDBC temporal fallo [phase=performance-smoke; category=jdbc-direct-probe; exitCode=$($process.ExitCode)]."
    } finally {
        if ($null -ne $process) {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
            $process.Dispose()
        }
        if ($null -ne $startInfo) { $startInfo.EnvironmentVariables.Remove("TEMPORARY_JDBC_PROBE_PASSWORD") }
        $temporaryDatabasePassword = $null
        if ($null -ne $probe) { Remove-TemporaryJdbcProbe $probe.Directory }
    }
}

function Invoke-TemporaryPsql {
    param([string[]]$PsqlArguments, [string]$Operation)
    Assert-TemporaryPostgresIdentity $Operation
    return Invoke-Docker -Arguments (@("exec", $script:temporaryContainerId, "psql", "-X", "-v", "ON_ERROR_STOP=1",
            "-U", "performance_user", "-d", "devchester_performance") + $PsqlArguments) -TimeoutSeconds $temporaryPsqlTimeoutSeconds -Operation "psql"
}

function Test-TemporaryFlywayState {
    param([string]$Operation)
    $query = @"
SELECT COUNT(*) FILTER (WHERE version IS NOT NULL) = 23
   AND COUNT(*) FILTER (WHERE version IS NOT NULL AND success = FALSE) = 0
   AND COUNT(*) FILTER (WHERE version = '23' AND success = TRUE) = 1
FROM flyway_schema_history;
"@
    $result = Invoke-TemporaryPsql -PsqlArguments @("-tAc", $query) -Operation $Operation
    if ($result.ExitCode -ne 0 -or $result.Output.Trim() -ne "t") {
        Stop-WithError "Flyway no confirma V1-V23 sin errores [phase=performance-smoke; category=flyway-validation; exitCode=$($result.ExitCode)]."
    }
}

function Copy-TemporaryFlywayRunnerEntry {
    param([object]$Entry, [string]$DestinationPath)
    $destinationDirectory = Split-Path $DestinationPath -Parent
    [System.IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
    $input = $Entry.Open()
    $output = [System.IO.File]::Open($DestinationPath, [System.IO.FileMode]::CreateNew)
    try {
        $input.CopyTo($output)
    } finally {
        $output.Dispose()
        $input.Dispose()
    }
}

function New-TemporaryFlywayRunner {
    $directory = Join-Path ([System.IO.Path]::GetTempPath()) ("obs-us4-flyway-" + [Guid]::NewGuid().ToString("N"))
    $libraryDirectory = Join-Path $directory "lib"
    $resourcesDirectory = Join-Path $directory "resources"
    $sourcePath = Join-Path $directory "TemporaryFlywayMigration.java"
    $requiredLibraries = @(
        "flyway-core-11.7.2.jar",
        "flyway-database-postgresql-11.7.2.jar",
        "postgresql-42.7.11.jar",
        "slf4j-api-2.0.18.jar",
        "jackson-annotations-2.21.jar",
        "jackson-core-2.21.4.jar",
        "jackson-databind-2.21.4.jar",
        "jackson-dataformat-toml-2.21.4.jar",
        "jackson-datatype-jsr310-2.21.4.jar"
    )
    try {
        [System.IO.Directory]::CreateDirectory($libraryDirectory) | Out-Null
        [System.IO.Directory]::CreateDirectory($resourcesDirectory) | Out-Null
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($backendJarPath)
        try {
            foreach ($libraryName in $requiredLibraries) {
                $entries = @($archive.Entries | Where-Object { $_.FullName -eq ("BOOT-INF/lib/" + $libraryName) })
                if ($entries.Count -ne 1) {
                    Stop-WithError "El bootJar no contiene la dependencia Flyway requerida $libraryName [phase=performance-smoke; category=flyway-runner-dependency; exitCode=n/a]."
                }
                Copy-TemporaryFlywayRunnerEntry -Entry $entries[0] -DestinationPath (Join-Path $libraryDirectory $libraryName)
            }
            $migrationEntries = @($archive.Entries | Where-Object { $_.FullName -match '^BOOT-INF/classes/db/migration/V\d+__[^/]+\.sql$' })
            if ($migrationEntries.Count -ne 23) {
                Stop-WithError "El bootJar no contiene exactamente las 23 migraciones Flyway esperadas [phase=performance-smoke; category=flyway-runner-migrations; exitCode=n/a]."
            }
            foreach ($migrationEntry in $migrationEntries) {
                $relativePath = $migrationEntry.FullName.Substring("BOOT-INF/classes/".Length)
                Copy-TemporaryFlywayRunnerEntry -Entry $migrationEntry -DestinationPath (Join-Path $resourcesDirectory $relativePath)
            }
        } finally {
            $archive.Dispose()
        }
        $source = @'
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.FlywayException;

public final class TemporaryFlywayMigration {
    private static final String PASSWORD_ENV = "TEMPORARY_FLYWAY_PASSWORD";
    private static final String EXPECTED_VERSION = "23";

    private static void fail(String outcome) {
        System.out.println("FLYWAY_RUNNER_FAIL; outcome=" + outcome);
        System.exit(2);
    }

    public static void main(String[] args) {
        if (args.length != 2 || System.getenv(PASSWORD_ENV) == null) {
            fail("configuration");
            return;
        }
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(args[0], args[1], System.getenv(PASSWORD_ENV))
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            MigrationInfo current = flyway.info().current();
            if (current == null || current.getVersion() == null
                    || !EXPECTED_VERSION.equals(current.getVersion().getVersion())) {
                fail("version");
                return;
            }
            System.out.println("FLYWAY_RUNNER_PASS; version=" + EXPECTED_VERSION);
        } catch (FlywayException exception) {
            fail("flyway");
        } catch (Exception exception) {
            fail("runner");
        }
    }
}
'@
        [System.IO.File]::WriteAllText($sourcePath, $source, (New-Object System.Text.UTF8Encoding($false)))
        return [pscustomobject]@{
            Directory = $directory
            SourcePath = $sourcePath
            ClassPath = (Join-Path $libraryDirectory "*") + ";" + $resourcesDirectory
        }
    } catch {
        if ([System.IO.Directory]::Exists($directory)) { [System.IO.Directory]::Delete($directory, $true) }
        throw
    }
}

function Remove-TemporaryFlywayRunner {
    param([string]$Directory)
    if ([string]::IsNullOrWhiteSpace($Directory) -or -not [System.IO.Directory]::Exists($Directory)) { return }
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $fullDirectory = [System.IO.Path]::GetFullPath($Directory).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $isExpectedDirectory = [System.IO.Path]::GetDirectoryName($fullDirectory) -eq $temporaryRoot -and [System.IO.Path]::GetFileName($fullDirectory) -like "obs-us4-flyway-*"
    if (-not $isExpectedDirectory) {
        Stop-WithError "Se rechazo la limpieza fuera del directorio temporal de Flyway [phase=cleanup; category=flyway-runner-cleanup; exitCode=n/a]."
    }
    [System.IO.Directory]::Delete($fullDirectory, $true)
}

function Invoke-TemporaryFlywayMigration {
    Assert-TemporaryPostgresIdentity "la ejecucion temporal de Flyway"
    $temporaryDatabasePassword = Get-TemporaryDatabasePassword
    $runner = $null
    try {
        $runner = New-TemporaryFlywayRunner
        $result = Invoke-NativeWithTimeout -FilePath "java.exe" -Arguments @(
            "-Duser.timezone=UTC",
            "--class-path",
            $runner.ClassPath,
            $runner.SourcePath,
            (Get-TemporaryJdbcUrl),
            "performance_user"
        ) -TimeoutSeconds $FlywayMigrationTimeoutSeconds -Operation "Flyway directo" -Environment @{
            "TEMPORARY_FLYWAY_PASSWORD" = $temporaryDatabasePassword
        }
        if ($result.ExitCode -ne 0 -or $result.Output -notmatch '(?m)^FLYWAY_RUNNER_PASS; version=23\s*$') {
            $category = if ($result.Output -match '(?m)^FLYWAY_RUNNER_FAIL; outcome=flyway\s*$') { "flyway-runner" } else { "flyway-runner-execution" }
            Stop-WithError "El ejecutor temporal de Flyway fallo [phase=performance-smoke; category=$category; exitCode=$($result.ExitCode)]."
        }
        Test-TemporaryFlywayState "la verificacion de migraciones"
        Write-PassMessage "Flyway directo aplico y valido V1-V23 en PostgreSQL temporal."
    } finally {
        $temporaryDatabasePassword = $null
        if ($null -ne $runner) { Remove-TemporaryFlywayRunner $runner.Directory }
    }
}

function Invoke-PerformanceSeedSmoke {
    Test-TemporaryFlywayState "el seed temporal"
    $result = Invoke-TemporaryPsql -PsqlArguments @("-f", "/performance/seed.sql") -Operation "el seed temporal"
    if ($result.ExitCode -ne 0) {
        Stop-WithError "El seed de performance fallo [phase=performance-smoke; category=psql-seed; exitCode=$($result.ExitCode)]."
    }
    Test-TemporaryFlywayState "la validacion posterior al seed"
    $counts = Invoke-TemporaryPsql -PsqlArguments @("-tAc",
        "SELECT (SELECT COUNT(*) FROM equipos) = 400 AND (SELECT COUNT(*) FROM jugadores) = 10000 AND (SELECT COUNT(*) FROM usuarios) = 1000 AND (SELECT COUNT(*) FROM api_keys) = 0") -Operation "la validacion de conteos"
    if ($counts.ExitCode -ne 0 -or $counts.Output.Trim() -ne "t") {
        Stop-WithError "Los conteos del seed temporal no coinciden [phase=performance-smoke; category=seed-counts; exitCode=$($counts.ExitCode)]."
    }
    Write-PassMessage "El Compose temporal, Flyway y seed fueron comprobados como smoke de infraestructura; no es un benchmark."
}

function Start-PerformanceSmoke {
    Assert-TemporaryProjectUnused
    $script:temporaryProjectCreated = $true
    $result = Invoke-PerformanceCompose @("up", "--detach", "postgres")
    if ($result.ExitCode -ne 0) {
        Stop-WithError "No se pudo iniciar PostgreSQL temporal de performance [phase=performance-smoke; category=compose-up; exitCode=$($result.ExitCode)]."
    }
    $script:temporaryContainerId = Get-TemporaryContainerId
    Wait-ForTemporaryPostgres
    Assert-TemporaryPostgresWindowsConnectivity
    Invoke-TemporaryJdbcProbe
    Invoke-TemporaryFlywayMigration
    Invoke-PerformanceSeedSmoke
}

function Stop-PerformanceSmoke {
    $result = Invoke-PerformanceCompose @("down", "--volumes", "--remove-orphans", "--timeout", $cleanupTimeoutSeconds)
    if ($result.ExitCode -ne 0) {
        Stop-WithError "La limpieza del proyecto temporal de performance fallo [phase=cleanup; category=compose-cleanup; exitCode=$($result.ExitCode)]."
    }
}

function Get-SanitizedDiagnostic {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord, [string]$Phase)
    $message = $ErrorRecord.Exception.Message
    $categoryMatch = [regex]::Match($message, "category=(?<category>[a-z-]+)")
    $exitCodeMatch = [regex]::Match($message, "exitCode=(?<exitCode>-?\d+|n/a)")
    $category = if ($categoryMatch.Success) { $categoryMatch.Groups["category"].Value } else { "validation-check" }
    $exitCode = if ($exitCodeMatch.Success) { $exitCodeMatch.Groups["exitCode"].Value } else { "n/a" }
    $safeDiagnostic = if ($category -eq "jdbc-direct-probe") { $script:temporaryJdbcProbeDiagnostic } else { $null }
    if ([string]::IsNullOrWhiteSpace($safeDiagnostic)) {
        return "phase=$Phase; category=$category; exitCode=$exitCode"
    }
    return "phase=$Phase; category=$category; exitCode=$exitCode; $safeDiagnostic"
}

Assert-CommandAvailable "docker.exe"
Assert-CommandAvailable "java.exe"
Assert-DockerAvailable
Assert-RequiredFile $performanceComposePath "Compose de performance"
Assert-RequiredFile $performanceSeedPath "seed.sql de performance"
Assert-RequiredFile $backendJarPath "artefacto backend para migraciones"
Assert-ComposeFile $performanceComposePath
Assert-PerformancePortAvailable
if ($ValidationMode -eq "all") {
    Assert-CommandAvailable "npm.cmd"
    Assert-RequiredFile $frontendComposePath "Compose E2E de frontend"
    Assert-RequiredFile (Join-Path $frontendPath "node_modules") "dependencias frontend instaladas"
    Assert-RequiredFile (Join-Path $frontendPath "node_modules/@playwright/test") "runtime de Playwright instalado"
    Assert-ComposeFile $frontendComposePath
    Assert-FrontendE2EProjectIdle
}

$primaryFailure = $null
$cleanupFailures = @()
try {
    if ($ValidationMode -eq "all") {
        Invoke-FrontendE2E
    }
    Start-PerformanceSmoke
} catch {
    $primaryFailure = Get-SanitizedDiagnostic $_ "validation"
} finally {
    if ($script:temporaryProjectCreated) {
        try { Stop-PerformanceSmoke } catch { $cleanupFailures += Get-SanitizedDiagnostic $_ "docker-cleanup" }
    }
}

if ($null -ne $primaryFailure -and $cleanupFailures.Count -gt 0) {
    Stop-WithError "La validacion y la limpieza temporal fallaron [$primaryFailure] [$($cleanupFailures -join '; ')]."
}
if ($cleanupFailures.Count -gt 0) {
    Stop-WithError "La limpieza temporal fallo [$($cleanupFailures -join '; ')]."
}
if ($null -ne $primaryFailure) {
    Stop-WithError "La validacion de suites existentes fallo [$primaryFailure]."
}
Write-PassMessage "Las suites existentes y el smoke de infraestructura finalizaron sin modificar recursos Docker ajenos."
