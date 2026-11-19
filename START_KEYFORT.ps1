# KeyFort - Server Runner (PowerShell)
$Host.UI.RawUI.WindowTitle = "KeyFort Server (Port 8080)"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "            KeyFort Zero-Knowledge Platform             " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

$root = $PSScriptRoot
if (-not $root) { $root = Get-Location }
Set-Location $root

Write-Host "Starting KeyFort Backend Server on http://localhost:8080 ...`n" -ForegroundColor Green
Write-Host "Once you see 'Started KeyFortServerApplication', open this in your browser:" -ForegroundColor Yellow
Write-Host "http://localhost:8080/ledger/verify`n" -ForegroundColor Cyan
Write-Host "[Press Ctrl+C at any time to stop the server]" -ForegroundColor DarkGray
Write-Host "--------------------------------------------------------`n" -ForegroundColor DarkGray

& .\gradlew.bat :keyfort-server:run

Write-Host "`nServer stopped." -ForegroundColor Yellow
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
