param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$StudentNo,
  [string]$Password,
  [int]$BizLogSize = 20
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StudentNo) -or [string]::IsNullOrWhiteSpace($Password)) {
  throw "StudentNo and Password are required. Example: .\scripts\verify-monitoring-endpoints.ps1 -StudentNo 20260001 -Password 123456"
}

Write-Host "[1/4] Logging in as $StudentNo ..."
$loginPayload = @{ studentNo = $StudentNo; password = $Password } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Method Post -Uri "$BaseUrl/user/login" -ContentType "application/json" -Body $loginPayload

if ($loginRes.code -ne 200 -or [string]::IsNullOrWhiteSpace($loginRes.data.token)) {
  throw "Login failed: $($loginRes.message)"
}

$token = $loginRes.data.token
$headers = @{ Authorization = "Bearer $token" }

Write-Host "[2/4] Checking monitoring dashboard endpoint ..."
$dashboard = Invoke-RestMethod -Method Get -Uri "$BaseUrl/monitoring/dashboard?timeRange=yearly" -Headers $headers
if ($dashboard.code -ne 200) {
  throw "Dashboard endpoint failed: $($dashboard.message)"
}

Write-Host "[3/4] Checking developer metrics endpoint ..."
$devMetrics = Invoke-RestMethod -Method Get -Uri "$BaseUrl/monitoring/developer-metrics" -Headers $headers
if ($devMetrics.code -ne 200) {
  throw "Developer metrics endpoint failed: $($devMetrics.message)"
}

Write-Host "[4/4] Checking business logs endpoint ..."
$bizLogs = Invoke-RestMethod -Method Get -Uri "$BaseUrl/monitoring/business-logs?size=$BizLogSize" -Headers $headers
if ($bizLogs.code -ne 200) {
  throw "Business logs endpoint failed: $($bizLogs.message)"
}

$records = @($bizLogs.data)
$unknownCount = ($records | Where-Object { $_.operatorStudentNo -eq "anonymous" -or $_.operatorRole -eq "unknown" }).Count

Write-Host ""
Write-Host "Verification Summary"
Write-Host "- Dashboard: OK"
Write-Host "- Developer metrics: OK"
Write-Host "- Business logs count: $($records.Count)"
Write-Host "- Unknown operator rows: $unknownCount"

if ($unknownCount -gt 0) {
  Write-Warning "Found rows with anonymous/unknown operator identity. Please re-check AOP annotation coverage and SecurityContext propagation."
}

Write-Host "Done."
