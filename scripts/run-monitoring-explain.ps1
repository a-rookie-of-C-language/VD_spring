param(
    [string]$HostName = $env:DB_HOST,
    [int]$Port = 3306,
    [string]$Database = $env:DB_NAME,
    [string]$User = $env:DB_USER,
    [string]$Password = $env:DB_PASSWORD,
    [string]$SqlFile = "src/main/resources/db/perf/monitoring_explain_baseline.sql",
    [string]$QueryFile = "src/main/resources/db/perf/monitoring_explain_queries.sql",
    [string]$OutputDir = "tmp/perf",
    [switch]$DryRun
)

if ([string]::IsNullOrWhiteSpace($HostName)) { $HostName = "localhost" }
if ([string]::IsNullOrWhiteSpace($Database)) { $Database = "VD" }
if ([string]::IsNullOrWhiteSpace($User)) { $User = "root" }

$root = Split-Path -Parent $PSScriptRoot
$sqlPath = Join-Path $root $SqlFile
$queryPath = Join-Path $root $QueryFile
$outPath = Join-Path $root $OutputDir

if (!(Test-Path -LiteralPath $sqlPath)) {
    throw "SQL file not found: $sqlPath"
}

if (!(Test-Path -LiteralPath $queryPath)) {
    throw "Query file not found: $queryPath"
}

if (!(Test-Path -LiteralPath $outPath)) {
    New-Item -ItemType Directory -Path $outPath | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $outPath "monitoring-explain-$timestamp.txt"
$summaryPath = Join-Path $outPath "monitoring-explain-summary-$timestamp.md"
$csvPath = Join-Path $outPath "monitoring-explain-summary-$timestamp.csv"

$mysqlArgs = @(
    "-h", $HostName,
    "-P", $Port,
    "-u", $User,
    "--database", $Database,
    "--table",
    "--show-warnings"
)

if (![string]::IsNullOrWhiteSpace($Password)) {
    $mysqlArgs += "--password=$Password"
}

$mysqlArgs += "-e"
$mysqlArgs += "source $sqlPath"

function Get-SqlStatements {
    param([string]$Path)

    $lines = Get-Content -LiteralPath $Path -Encoding UTF8
    $buffer = [System.Collections.Generic.List[string]]::new()
    $statements = [System.Collections.Generic.List[string]]::new()

    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0) { continue }
        if ($trimmed.StartsWith("--")) { continue }
        $buffer.Add($line)
        if ($trimmed.EndsWith(";")) {
            $sql = ($buffer -join "`n").Trim()
            if ($sql.EndsWith(";")) {
                $sql = $sql.Substring(0, $sql.Length - 1)
            }
            if ($sql.Length -gt 0) {
                $statements.Add($sql)
            }
            $buffer.Clear()
        }
    }

    if ($buffer.Count -gt 0) {
        $sql = ($buffer -join "`n").Trim()
        if ($sql.Length -gt 0) {
            $statements.Add($sql)
        }
    }

    return $statements
}

function Get-PropertyValue {
    param(
        [object]$Node,
        [string]$Name
    )

    if ($null -eq $Node) { return $null }
    $prop = $Node.PSObject.Properties[$Name]
    if ($null -eq $prop) { return $null }
    return $prop.Value
}

function Collect-ExplainRows {
    param(
        [object]$Node,
        [string]$QueryName,
        [System.Collections.Generic.List[object]]$Rows,
        [Nullable[int]]$SelectId
    )

    if ($null -eq $Node) { return }

    if ($Node -is [string]) { return }

    if ($Node -is [System.Collections.IEnumerable] -and !($Node -is [System.Collections.IDictionary]) -and !($Node -is [pscustomobject])) {
        foreach ($item in $Node) {
            Collect-ExplainRows -Node $item -QueryName $QueryName -Rows $Rows -SelectId $SelectId
        }
        return
    }

    $currentSelect = $SelectId
    $sid = Get-PropertyValue -Node $Node -Name "select_id"
    if ($null -ne $sid -and "$sid" -match '^\d+$') {
        $currentSelect = [int]$sid
    }

    $tableName = Get-PropertyValue -Node $Node -Name "table_name"
    if ($null -ne $tableName) {
        $Rows.Add([pscustomobject]@{
            Query = $QueryName
            SelectId = if ($null -ne $currentSelect) { $currentSelect } else { "-" }
            Table = $tableName
            AccessType = (Get-PropertyValue -Node $Node -Name "access_type")
            Key = (Get-PropertyValue -Node $Node -Name "key")
            RowsExamined = (Get-PropertyValue -Node $Node -Name "rows_examined_per_scan")
            Filtered = (Get-PropertyValue -Node $Node -Name "filtered")
        })
    }

    foreach ($prop in $Node.PSObject.Properties) {
        Collect-ExplainRows -Node $prop.Value -QueryName $QueryName -Rows $Rows -SelectId $currentSelect
    }
}

function Build-MarkdownSummary {
    param(
        [System.Collections.Generic.List[object]]$Rows,
        [string]$Path,
        [string]$RawReportPath,
        [string]$QuerySourcePath
    )

    $content = [System.Collections.Generic.List[string]]::new()
    $content.Add("# Monitoring EXPLAIN Summary")
    $content.Add("")
    $content.Add("- Raw report: $RawReportPath")
    $content.Add("- Query source: $QuerySourcePath")
    $content.Add("- Generated at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $content.Add("")
    $content.Add("| Query | SelectId | Table | AccessType | Key | RowsExamined | Filtered |")
    $content.Add("|---|---:|---|---|---|---:|---:|")

    foreach ($row in $Rows) {
        $query = "$($row.Query)"
        $selectId = "$($row.SelectId)"
        $table = "$($row.Table)"
        $accessType = "$($row.AccessType)"
        $key = "$($row.Key)"
        $rowsExamined = "$($row.RowsExamined)"
        $filtered = "$($row.Filtered)"
        if ([string]::IsNullOrWhiteSpace($key)) { $key = "-" }
        if ([string]::IsNullOrWhiteSpace($rowsExamined)) { $rowsExamined = "-" }
        if ([string]::IsNullOrWhiteSpace($filtered)) { $filtered = "-" }
        if ([string]::IsNullOrWhiteSpace($accessType)) { $accessType = "-" }
        $content.Add("| $query | $selectId | $table | $accessType | $key | $rowsExamined | $filtered |")
    }

    $content | Set-Content -LiteralPath $Path -Encoding UTF8
}

if ($DryRun) {
    "TRADITIONAL_EXPLAIN_CMD: mysql $($mysqlArgs -join ' ')"
    $queries = Get-SqlStatements -Path $queryPath
    "JSON_EXPLAIN_QUERY_COUNT: $($queries.Count)"
    exit 0
}

$result = & mysql @mysqlArgs 2>&1
$result | Set-Content -LiteralPath $reportPath -Encoding UTF8

if ($LASTEXITCODE -ne 0) {
    throw "mysql exited with code $LASTEXITCODE. Output: $reportPath"
}

$queryStatements = Get-SqlStatements -Path $queryPath
$summaryRows = [System.Collections.Generic.List[object]]::new()

$jsonMysqlArgs = @(
    "-h", $HostName,
    "-P", $Port,
    "-u", $User,
    "--database", $Database,
    "--batch",
    "--raw",
    "--skip-column-names",
    "--show-warnings"
)

if (![string]::IsNullOrWhiteSpace($Password)) {
    $jsonMysqlArgs += "--password=$Password"
}

$queryIndex = 0
foreach ($statement in $queryStatements) {
    $queryIndex += 1
    $queryName = "Q$queryIndex"
    $jsonQuery = "EXPLAIN FORMAT=JSON $statement"
    $queryArgs = @($jsonMysqlArgs)
    $queryArgs += "-e"
    $queryArgs += $jsonQuery

    $jsonOutput = & mysql @queryArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "mysql JSON EXPLAIN failed for $queryName. Raw report: $reportPath"
    }

    $jsonText = ($jsonOutput | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($jsonText)) {
        continue
    }

    $parsed = $jsonText | ConvertFrom-Json
    Collect-ExplainRows -Node $parsed -QueryName $queryName -Rows $summaryRows -SelectId $null
}

Build-MarkdownSummary -Rows $summaryRows -Path $summaryPath -RawReportPath $reportPath -QuerySourcePath $queryPath
$summaryRows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

Write-Output "EXPLAIN report generated: $reportPath"
Write-Output "Summary generated: $summaryPath"
Write-Output "CSV generated: $csvPath"
