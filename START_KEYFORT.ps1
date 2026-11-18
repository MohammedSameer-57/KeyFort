# KeyFort - Universal Launcher (PowerShell)
$Host.UI.RawUI.WindowTitle = "KeyFort - Universal Launcher"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "            KeyFort Zero-Knowledge Platform             " -ForegroundColor Cyan
Write-Host "                   Universal Launcher                   " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

$root = $PSScriptRoot
if (-not $root) { $root = Get-Location }
Set-Location $root

# 1. Check Java
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd -and -not $env:JAVA_HOME) {
    Write-Host "[ERROR] Java 17+ is required but not installed on this machine!" -ForegroundColor Red
    Write-Host "`nTo run KeyFort on your laptop:" -ForegroundColor Yellow
    Write-Host "1. Download OpenJDK 17: https://adoptium.net/temurin/releases/?version=17"
    Write-Host "2. Run the installer (takes 1 minute)`n"
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}
Write-Host "[OK] Java environment detected." -ForegroundColor Green

# 2. Check Android ADB
$adb = "adb"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    $fallbackAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $fallbackAdb) { $adb = $fallbackAdb }
}

# 3. Start Backend Server
Write-Host "`n[1/2] Checking KeyFort Backend Server (port 8080)..." -NoNewline
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/ledger/verify" -TimeoutSec 2 -ErrorAction Stop
    Write-Host " [OK] Server already running." -ForegroundColor Green
} catch {
    Write-Host " [STARTING]" -ForegroundColor Yellow
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c gradlew.bat :keyfort-server:run" -WindowStyle Minimized
    Start-Sleep -Seconds 6
    Write-Host "      [OK] Server started on http://localhost:8080" -ForegroundColor Green
}

# 4. Check Android Emulator/Device
Write-Host "`n[2/2] Checking Android device / emulator..." -NoNewline
$deviceOnline = $false
try {
    $devices = & $adb devices 2>$null
    if ($devices -match "\bdevice\b") {
        $deviceOnline = $true
        Write-Host " [OK] Device detected. Launching app..." -ForegroundColor Green
        & $adb shell am start -n org.keyfort.android/.ui.UnlockActivity | Out-Null
    }
} catch {}

if (-not $deviceOnline) {
    Write-Host " [INFO] No running Android emulator found." -ForegroundColor DarkYellow
    Write-Host "       (Mobile app can be opened in Android Studio when needed)." -ForegroundColor DarkGray
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  KeyFort Backend is LIVE!" -ForegroundColor Green
Write-Host "  - Backend URL : http://localhost:8080"
Write-Host "  - Audit Log   : http://localhost:8080/ledger/verify"
Write-Host "========================================================`n" -ForegroundColor Cyan

Start-Process "http://localhost:8080/ledger/verify"
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
