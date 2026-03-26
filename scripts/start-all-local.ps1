param(
    [string]$ElasticsearchBin = "D:\elasticsearch-9.3.1\bin",
    [string]$LogstashBin = "D:\logstash-9.3.1\bin",
    [string]$KibanaBin = "D:\kibana-9.3.1\bin",
    [int]$EsPortWaitSeconds = 300,
    [int]$EsHttpWaitSeconds = 120,
    [switch]$EnableEsSecurity,
    [switch]$StartFrontend,
    [switch]$SkipBackend,
    [switch]$SkipKibana,
    [switch]$DryRun
)

$defaultElasticsearchBin = "D:\elasticsearch-9.3.1\bin"
$defaultLogstashBin = "D:\logstash-9.3.1\bin"
$defaultKibanaBin = "D:\kibana-9.3.1\bin"

$flagTokens = [System.Collections.Generic.List[string]]::new()
foreach ($token in @($ElasticsearchBin, $LogstashBin, $KibanaBin)) {
    if ("$token" -like "--*") {
        $flagTokens.Add("$token")
    }
}
foreach ($token in $args) {
    $flagTokens.Add("$token")
}

if ("$ElasticsearchBin" -like "--*") { $ElasticsearchBin = $defaultElasticsearchBin }
if ("$LogstashBin" -like "--*") { $LogstashBin = $defaultLogstashBin }
if ("$KibanaBin" -like "--*") { $KibanaBin = $defaultKibanaBin }

if ($flagTokens -contains "--StartFrontend") { $StartFrontend = $true }
if ($flagTokens -contains "--SkipBackend") { $SkipBackend = $true }
if ($flagTokens -contains "--SkipKibana") { $SkipKibana = $true }
if ($flagTokens -contains "--EnableEsSecurity") { $EnableEsSecurity = $true }
if ($flagTokens -contains "--DryRun") { $DryRun = $true }

$rawLine = "$($MyInvocation.Line)"
if ($rawLine -match "--StartFrontend") { $StartFrontend = $true }
if ($rawLine -match "--SkipBackend") { $SkipBackend = $true }
if ($rawLine -match "--SkipKibana") { $SkipKibana = $true }
if ($rawLine -match "--EnableEsSecurity") { $EnableEsSecurity = $true }
if ($rawLine -match "--DryRun") { $DryRun = $true }

function Wait-PortReady {
    param(
        [int]$Port,
        [int]$TimeoutSeconds,
        [string]$Name
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($listening) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not become ready on port $Port within ${TimeoutSeconds}s"
}

function Get-ElasticsearchDiagnostics {
    param([string]$EsBinPath)

    $result = [System.Collections.Generic.List[string]]::new()
    $esHome = Split-Path -Parent $EsBinPath
    $logsDir = Join-Path $esHome "logs"

    $result.Add("Diagnostics: Elasticsearch home = $esHome")
    $result.Add("Diagnostics: Elasticsearch logs dir = $logsDir")

    if (Test-Path -LiteralPath $logsDir) {
        $latestLog = Get-ChildItem -LiteralPath $logsDir -Filter "*.log" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if ($latestLog) {
            $result.Add("Diagnostics: Latest log = $($latestLog.FullName)")
            $result.Add("Diagnostics: Last 40 lines:")
            $tail = Get-Content -LiteralPath $latestLog.FullName -Tail 40 -ErrorAction SilentlyContinue
            foreach ($line in $tail) {
                $result.Add("  $line")
            }
        } else {
            $result.Add("Diagnostics: No .log files found in Elasticsearch logs directory")
        }
    } else {
        $result.Add("Diagnostics: Elasticsearch logs directory not found")
    }

    $esJava = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.Path -and $_.Path.StartsWith((Join-Path (Split-Path -Parent $EsBinPath) "jdk"), [System.StringComparison]::OrdinalIgnoreCase)
    }

    if ($esJava) {
        $result.Add("Diagnostics: Elasticsearch Java processes detected:")
        foreach ($p in $esJava) {
            $result.Add("  PID=$($p.Id) Path=$($p.Path)")
        }
    } else {
        $result.Add("Diagnostics: No Elasticsearch Java process detected")
    }

    return $result
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds,
        [string]$Name
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 700
            continue
        }
        Start-Sleep -Milliseconds 700
    }
    throw "$Name did not become HTTP-ready: $Url within ${TimeoutSeconds}s"
}

function Test-CommandAvailable {
    param([string]$Name)
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    return $null -ne $cmd
}

$root = Split-Path -Parent $PSScriptRoot
$startElkScript = Join-Path $PSScriptRoot "start-elk-local.ps1"

if (!(Test-Path -LiteralPath $startElkScript)) {
    throw "Script not found: $startElkScript"
}

$backendDir = $root
$frontendDir = Join-Path $root "VD"

if (!(Test-Path -LiteralPath (Join-Path $backendDir "pom.xml"))) {
    throw "Backend pom.xml not found: $backendDir"
}

if ($StartFrontend -and !(Test-Path -LiteralPath (Join-Path $frontendDir "package.json"))) {
    throw "Frontend package.json not found: $frontendDir"
}

if (!$SkipBackend -and !(Test-CommandAvailable -Name "mvn")) {
    throw "Command not found: mvn. Please install Maven or add it to PATH."
}

if ($StartFrontend -and !(Test-CommandAvailable -Name "npm")) {
    throw "Command not found: npm. Please install Node.js or add npm to PATH."
}

$elkArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$startElkScript`"",
    "-ElasticsearchBin", "`"$ElasticsearchBin`"",
    "-LogstashBin", "`"$LogstashBin`""
)

if (!$SkipKibana) {
    $elkArgs += "-KibanaBin"
    $elkArgs += "`"$KibanaBin`""
    $elkArgs += "-StartKibana"
}

if (!$EnableEsSecurity) {
    $elkArgs += "-DisableEsSecurity"
}

if ($DryRun) {
    $elkArgs += "-DryRun"
}

$commands = [System.Collections.Generic.List[string]]::new()
$commands.Add("powershell $($elkArgs -join ' ')")

if (!$SkipBackend) {
    $commands.Add("mvn spring-boot:run")
}

if ($StartFrontend) {
    $commands.Add("npm run dev")
}

if ($DryRun) {
    $commands
    exit 0
}

Start-Process -FilePath "powershell" -ArgumentList $elkArgs -WorkingDirectory $root | Out-Null

try {
    Wait-PortReady -Port 9200 -TimeoutSeconds $EsPortWaitSeconds -Name "Elasticsearch HTTP port"
} catch {
    $diag = Get-ElasticsearchDiagnostics -EsBinPath $ElasticsearchBin
    foreach ($line in $diag) {
        Write-Host $line
    }
    throw
}
if (!$EnableEsSecurity) {
    Wait-HttpReady -Url "http://localhost:9200" -TimeoutSeconds $EsHttpWaitSeconds -Name "Elasticsearch endpoint"
}
Wait-PortReady -Port 5000 -TimeoutSeconds 90 -Name "Logstash TCP input"

if (!$SkipBackend) {
    $backendCmd = "cd /d `"$backendDir`" && mvn spring-boot:run"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", $backendCmd -WorkingDirectory $backendDir | Out-Null
}

if ($StartFrontend) {
    $frontendCmd = "cd /d `"$frontendDir`" && npm run dev"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", $frontendCmd -WorkingDirectory $frontendDir | Out-Null
}

Write-Output "Started local ELK stack"
if (!$SkipBackend) {
    Write-Output "Started backend with mvn spring-boot:run"
}
if ($StartFrontend) {
    Write-Output "Started frontend with npm run dev"
}
