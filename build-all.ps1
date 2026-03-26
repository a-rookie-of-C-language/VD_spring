# ============================================================
#  Volunteer Duration - 一键构建并打包到 dist-bundle
#  用法: .\build-all.ps1 [-SkipBackend] [-SkipFrontend] [-SkipElectron]
# ============================================================
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipElectron
)

$ErrorActionPreference = "Stop"
$ROOT = $PSScriptRoot
$VD_DIR = Join-Path $ROOT "VD"
$DIST = Join-Path $ROOT "dist-bundle"

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-OK($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "    [FAIL] $msg" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "  ======================================================" -ForegroundColor Yellow
Write-Host "    Volunteer Duration - Build & Package" -ForegroundColor Yellow
Write-Host "  ======================================================" -ForegroundColor Yellow

# ----------------------------------------------------------
# 1. Backend (Maven)
# ----------------------------------------------------------
if (-not $SkipBackend) {
    Write-Step "1/3  Building Backend (Maven) ..."
    Push-Location $ROOT
    mvn package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { Write-Fail "Maven build failed" }
    $jar = Join-Path $ROOT "target\volunteer-duration-0.1.0.jar"
    $dest = Join-Path $DIST "backend\app.jar"
    Copy-Item $jar $dest -Force
    $size = [math]::Round((Get-Item $dest).Length / 1MB, 1)
    Write-OK "app.jar copied (${size} MB)"
    Pop-Location
}

# ----------------------------------------------------------
# 2. Frontend Web (Vite)
# ----------------------------------------------------------
if (-not $SkipFrontend) {
    Write-Step "2/3  Building Frontend Web (Vite) ..."
    Push-Location $VD_DIR
    npm run build
    if ($LASTEXITCODE -ne 0) { Write-Fail "Vite build failed" }
    $webDest = Join-Path $DIST "frontend\web"
    Copy-Item "dist\*" $webDest -Recurse -Force
    Write-OK "dist/ copied to dist-bundle/frontend/web/"
    Pop-Location
}

# ----------------------------------------------------------
# 3. Frontend Electron (electron-builder)
# ----------------------------------------------------------
if (-not $SkipElectron) {
    Write-Step "3/3  Building Electron App ..."

    # 输出到 C:\Temp 避开 VS Code / 百度网盘的文件监听（避免 EBUSY on app.asar）
    $buildOut = "C:\Temp\vd-electron-build"
    $winUnpacked = "$buildOut\win-unpacked"
    if (Test-Path $buildOut) {
        $oldPref = $ErrorActionPreference; $ErrorActionPreference = 'SilentlyContinue'
        cmd /c "rmdir /s /q `"$buildOut`"" 2>&1 | Out-Null
        $ErrorActionPreference = $oldPref
        if (Test-Path $buildOut) { Write-Fail "Cannot delete $buildOut (file still locked)." }
        Write-Host "    Cleaned $buildOut" -ForegroundColor DarkGray
    }
    Push-Location $VD_DIR
    npx electron-builder
    if ($LASTEXITCODE -ne 0) { Write-Fail "electron-builder failed" }
    $electronDest = Join-Path $DIST "frontend\electron"
    Copy-Item "$winUnpacked\*" $electronDest -Recurse -Force
    $exe = Join-Path $electronDest "VolunteerDashboard.exe"
    $size = [math]::Round((Get-Item $exe).Length / 1MB, 1)
    Write-OK "win-unpacked copied (VolunteerDashboard.exe ${size} MB)"
    Pop-Location
}

# ----------------------------------------------------------
# Done
# ----------------------------------------------------------
Write-Host ""
Write-Host "  ======================================================" -ForegroundColor Green
Write-Host "    Build Complete!" -ForegroundColor Green
Write-Host "  ------------------------------------------------------" -ForegroundColor Green
if (-not $SkipBackend)   { Write-Host "    Backend  : dist-bundle\backend\app.jar" -ForegroundColor Green }
if (-not $SkipFrontend)  { Write-Host "    Web      : dist-bundle\frontend\web\" -ForegroundColor Green }
if (-not $SkipElectron)  { Write-Host "    Electron : dist-bundle\frontend\electron\" -ForegroundColor Green }
Write-Host "  ======================================================" -ForegroundColor Green
Write-Host ""
