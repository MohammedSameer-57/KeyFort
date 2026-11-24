# KeyFort Automated Demo Runner
$root = Resolve-Path "$PSScriptRoot\.."
Set-Location $root

Write-Host "Starting KeyFort Demo Environment..." -ForegroundColor Cyan

# 1. Clean existing run directory
$runDir = "$root\.run"
if (-not (Test-Path $runDir)) {
    New-Item -ItemType Directory -Path $runDir | Out-Null
}

# 2. Generate ephemeral high-entropy secrets for this session
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtBytes = New-Object byte[] 32
$connectorBytes = New-Object byte[] 32
$rng.GetBytes($jwtBytes)
$rng.GetBytes($connectorBytes)

$jwtSecret = ($jwtBytes | ForEach-Object { "{0:x2}" -f $_ }) -join ""
$connSecret = ($connectorBytes | ForEach-Object { "{0:x2}" -f $_ }) -join ""

$env:KEYFORT_JWT_SECRET = $jwtSecret
$env:KEYFORT_CONNECTOR_SECRET = $connSecret
$env:SPRING_PROFILES_ACTIVE = "dev,demo"
$env:GRADLE_USER_HOME = "$root\.gradle-home"

Write-Host "Generated ephemeral session credentials (not stored on disk)." -ForegroundColor Green

# 3. Ensure distributions are up to date
Write-Host "Verifying application distributions..." -ForegroundColor Cyan
& ".\gradlew.bat" :keyfort-demo-site:installDist :keyfort-server:installDist | Out-Null

# 4. Create dedicated launcher batch files in .run
$demoSiteScript = @"
@echo off
title KeyFort-Demo-Site
set "KEYFORT_JWT_SECRET=$jwtSecret"
set "KEYFORT_CONNECTOR_SECRET=$connSecret"
set "SPRING_PROFILES_ACTIVE=dev,demo"
cd /d "$root"
call "keyfort-demo-site\build\install\keyfort-demo-site\bin\keyfort-demo-site.bat" -Dspring.profiles.active=dev,demo -Dkeyfort.connector.secret=$connSecret
"@
Set-Content -Path "$runDir\start_demosite.bat" -Value $demoSiteScript -Encoding ascii

$serverScript = @"
@echo off
title KeyFort-Server
set "KEYFORT_JWT_SECRET=$jwtSecret"
set "KEYFORT_CONNECTOR_SECRET=$connSecret"
set "SPRING_PROFILES_ACTIVE=dev,demo"
cd /d "$root"
call "keyfort-server\build\install\keyfort-server\bin\keyfort-server.bat" -Dspring.profiles.active=dev,demo -Dkeyfort.connector.secret=$connSecret -Dkeyfort.jwt.secret=$jwtSecret
"@
Set-Content -Path "$runDir\start_server.bat" -Value $serverScript -Encoding ascii

# 5. Launch keyfort-demo-site (port 9090)
Write-Host "Launching keyfort-demo-site on port 9090..."
$demoSiteProc = Start-Process -FilePath "$runDir\start_demosite.bat" -WorkingDirectory $root -PassThru
$demoSiteProc.Id | Out-File -FilePath "$runDir\demosite.pid" -Encoding ascii

# 6. Launch keyfort-server (port 8080)
Write-Host "Launching keyfort-server on port 8080..."
$serverProc = Start-Process -FilePath "$runDir\start_server.bat" -WorkingDirectory $root -PassThru
$serverProc.Id | Out-File -FilePath "$runDir\server.pid" -Encoding ascii

# 7. Wait for both servers to be operational
Write-Host "Waiting for servers to become ready..." -ForegroundColor Yellow

$serverReady = $false
$demoSiteReady = $false
$maxAttempts = 40

for ($i = 0; $i -lt $maxAttempts; $i++) {
    Start-Sleep -Seconds 2
    if (-not $serverReady) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/crypto-facts" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($resp.StatusCode -eq 200) { $serverReady = $true; Write-Host " [READY] keyfort-server (port 8080)" -ForegroundColor Green }
        } catch {}
    }
    if (-not $demoSiteReady) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:9090/login" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($resp.StatusCode -eq 200) { $demoSiteReady = $true; Write-Host " [READY] keyfort-demo-site (port 9090)" -ForegroundColor Green }
        } catch {}
    }

    if ($serverReady -and $demoSiteReady) { break }
}

if ($serverReady) {
    Write-Host "Opening Live Demo at http://localhost:8080/demo..." -ForegroundColor Cyan
    Start-Process "http://localhost:8080/demo"
} else {
    Write-Host "Server startup timed out. Check command prompt windows for error details." -ForegroundColor Red
}
