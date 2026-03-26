param(
    [string]$ElasticsearchBin = "D:\elasticsearch-9.3.1\bin",
    [string]$LogstashBin = "D:\logstash-9.3.1\bin",
    [string]$KibanaBin = "D:\kibana-9.3.1\bin",
    [string]$LogstashPipeline = "deploy/elk/logstash/pipeline/logstash.conf",
    [string]$EsHost = "localhost",
    [int]$EsPort = 9200,
    [switch]$DisableEsSecurity,
    [switch]$StartKibana,
    [switch]$DryRun
)

$root = Split-Path -Parent $PSScriptRoot
$esBat = Join-Path $ElasticsearchBin "elasticsearch.bat"

if (!(Test-Path -LiteralPath $esBat)) {
    throw "Elasticsearch executable not found: $esBat"
}

$logstashBat = $null
if (![string]::IsNullOrWhiteSpace($LogstashBin)) {
    $candidate = Join-Path $LogstashBin "logstash.bat"
    if (!(Test-Path -LiteralPath $candidate)) {
        throw "Logstash executable not found: $candidate"
    }
    $logstashBat = $candidate
}

$kibanaBat = $null
if ($StartKibana) {
    if ([string]::IsNullOrWhiteSpace($KibanaBin)) {
        throw "KibanaBin is required when -StartKibana is set"
    }
    $candidate = Join-Path $KibanaBin "kibana.bat"
    if (!(Test-Path -LiteralPath $candidate)) {
        throw "Kibana executable not found: $candidate"
    }
    $kibanaBat = $candidate
}

$pipelinePath = Join-Path $root $LogstashPipeline
if ($logstashBat -and !(Test-Path -LiteralPath $pipelinePath)) {
    throw "Logstash pipeline file not found: $pipelinePath"
}

$actions = [System.Collections.Generic.List[string]]::new()
$actions.Add("Start Elasticsearch: $esBat")
if ($logstashBat) {
    $actions.Add("Start Logstash: $logstashBat -f $pipelinePath")
}
if ($kibanaBat) {
    $actions.Add("Start Kibana: $kibanaBat")
}

if ($DryRun) {
    $actions
    if ($DisableEsSecurity) {
        "Elasticsearch security: disabled for local development"
    }
    exit 0
}

$esArgs = @()
if ($DisableEsSecurity) {
    $esArgs += "-E"
    $esArgs += "xpack.security.enabled=false"
    $esArgs += "-E"
    $esArgs += "xpack.security.http.ssl.enabled=false"
}
Start-Process -FilePath $esBat -ArgumentList $esArgs -WorkingDirectory $ElasticsearchBin | Out-Null

if ($logstashBat) {
    # Wait for Elasticsearch to be HTTP-ready before starting Logstash
    $esUrl = "http://${EsHost}:$EsPort"
    Write-Output "Waiting for Elasticsearch at $esUrl ..."
    $esDeadline = (Get-Date).AddSeconds(180)
    $esReady = $false
    while ((Get-Date) -lt $esDeadline) {
        try {
            $resp = Invoke-WebRequest -Uri $esUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
                $esReady = $true
                break
            }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $esReady) {
        throw "Elasticsearch did not become ready at $esUrl within 180 seconds"
    }
    Write-Output "Elasticsearch is ready. Starting Logstash ..."

    $logstashEnvCmd = "`$env:LS_ES_HOST='$EsHost'; `$env:LS_ES_PORT='$EsPort'; & '$logstashBat' -f '$pipelinePath'"
    Start-Process -FilePath "powershell" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $logstashEnvCmd | Out-Null
}

if ($kibanaBat) {
    $kibanaWorkingDir = Split-Path -Parent $kibanaBat
    Start-Process -FilePath $kibanaBat -WorkingDirectory $kibanaWorkingDir | Out-Null
}

Write-Output "Started Elasticsearch on ${EsHost}:$EsPort"
if ($logstashBat) {
    Write-Output "Started Logstash TCP input on port 5000"
} else {
    Write-Output "Logstash not started. Pass -LogstashBin '<path>\\bin' to start it."
}
if ($kibanaBat) {
    Write-Output "Started Kibana on default port 5601"
}
