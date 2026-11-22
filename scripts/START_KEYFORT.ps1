# KeyFort Server Launcher
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

try {
    $conns = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
} catch {}

Write-Host "Starting KeyFort backend server on http://localhost:8080..."
& .\gradlew.bat :keyfort-server:run
