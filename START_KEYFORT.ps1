# KeyFort - Universal Launcher (PowerShell)
$Host.UI.RawUI.WindowTitle = "KeyFort - Universal Launcher"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "            KeyFort Zero-Knowledge Platform             " -ForegroundColor Cyan
Write-Host "                   Universal Launcher                   " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

$root = $PSScriptRoot
if (-not $root) { $root = Get-Location }
Set-Location $root

# 1. Auto-configure Java 17
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot") {
        $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    } elseif (Test-Path "C:\Program Files\Java\jdk-17") {
        $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $env:JAVA_HOME) {
    Write-Host "[ERROR] Java 17+ is required but not installed!" -ForegroundColor Red
    Write-Host "Please install Adoptium OpenJDK 17: https://adoptium.net/`n" -ForegroundColor Yellow
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}
Write-Host "[OK] Java 17 environment detected." -ForegroundColor Green

# 2. Check and start server
$serverRunning = $false
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/ledger/verify" -TimeoutSec 1 -ErrorAction Stop
    if ($res.valid -eq $true) { $serverRunning = $true }
} catch {}

if ($serverRunning) {
    Write-Host "[OK] KeyFort server is already running on port 8080." -ForegroundColor Green
} else {
    Write-Host "[..] Starting KeyFort Backend Server..." -ForegroundColor Yellow
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k gradlew.bat :keyfort-server:run" -WindowStyle Normal
    Write-Host "[..] Waiting for server to initialize..." -ForegroundColor Yellow
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        try {
            $check = Invoke-RestMethod -Uri "http://localhost:8080/ledger/verify" -TimeoutSec 1 -ErrorAction Stop
            if ($check.valid -eq $true) {
                $serverRunning = $true
                break
            }
        } catch {}
    }
}

if ($serverRunning) {
    Write-Host "`n========================================================" -ForegroundColor Cyan
    Write-Host "  KeyFort Backend is LIVE and verified!" -ForegroundColor Green
    Write-Host "  - Backend URL : http://localhost:8080"
    Write-Host "  - Audit Log   : http://localhost:8080/ledger/verify"
    Write-Host "========================================================`n" -ForegroundColor Cyan
    Start-Process "http://localhost:8080/ledger/verify"
} else {
    Write-Host "`n[!] Server is taking a moment to start. Check the server console window." -ForegroundColor Yellow
}

Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
