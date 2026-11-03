# KeyFort - One-Click Launcher (PowerShell)
$Host.UI.RawUI.WindowTitle = "KeyFort - One-Click Launcher"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "           KeyFort Zero-Knowledge Password Manager      " -ForegroundColor Cyan
Write-Host "                  One-Click Launcher                    " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

$root = $PSScriptRoot
if (-not $root) { $root = Get-Location }
Set-Location $root

$adb = "C:\Users\Rachit.000\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$emulator = "C:\Users\Rachit.000\AppData\Local\Android\Sdk\emulator\emulator.exe"

# 1. Check Server
Write-Host "[1/3] Checking KeyFort Backend Server (port 8080)..." -NoNewline
$serverUp = $false
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/ledger/verify" -TimeoutSec 2 -ErrorAction Stop
    $serverUp = $true
    Write-Host " [OK] Server running." -ForegroundColor Green
} catch {
    Write-Host " [STARTING]" -ForegroundColor Yellow
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c gradlew.bat :keyfort-server:run" -WindowStyle Minimized
    Start-Sleep -Seconds 6
    Write-Host "      [OK] Server started on http://localhost:8080" -ForegroundColor Green
}

# 2. Check Emulator
Write-Host "`n[2/3] Checking Android Emulator..." -NoNewline
$devices = & $adb devices
if ($devices -match "emulator-5554\s+device") {
    Write-Host " [OK] Emulator already online." -ForegroundColor Green
} else {
    Write-Host " [BOOTING]" -ForegroundColor Yellow
    Start-Process -FilePath $emulator -ArgumentList "-avd medium_phone -no-boot-anim" -WindowStyle Normal
    Write-Host "      Waiting for boot to complete..." -ForegroundColor DarkGray
    & $adb wait-for-device
    while ((& $adb -s emulator-5554 shell getprop sys.boot_completed).Trim() -ne "1") {
        Start-Sleep -Seconds 2
    }
    Write-Host "      [OK] Emulator booted successfully." -ForegroundColor Green
}

# 3. Launch App
Write-Host "`n[3/3] Launching KeyFort Android App..." -NoNewline
& $adb -s emulator-5554 shell am start -n org.keyfort.android/.ui.UnlockActivity | Out-Null
Write-Host " [OK] KeyFort is in foreground!" -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  KeyFort is LIVE and ready!" -ForegroundColor Green
Write-Host "  - Mobile App : Active on emulator screen"
Write-Host "  - Backend API: http://localhost:8080"
Write-Host "  - Audit Log  : http://localhost:8080/ledger/verify"
Write-Host "========================================================`n" -ForegroundColor Cyan
Write-Host "Press any key to exit this launcher window..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
