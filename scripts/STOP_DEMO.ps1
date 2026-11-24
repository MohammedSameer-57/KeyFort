# KeyFort Demo Termination Script
$root = Resolve-Path "$PSScriptRoot\.."
$runDir = "$root\.run"

Write-Host "Stopping KeyFort background processes..." -ForegroundColor Cyan

if (Test-Path "$runDir\server.pid") {
    $pid1 = Get-Content "$runDir\server.pid"
    try {
        Stop-Process -Id [int]$pid1 -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped keyfort-server (PID $pid1)" -ForegroundColor Green
    } catch {}
}

if (Test-Path "$runDir\demosite.pid") {
    $pid2 = Get-Content "$runDir\demosite.pid"
    try {
        Stop-Process -Id [int]$pid2 -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped keyfort-demo-site (PID $pid2)" -ForegroundColor Green
    } catch {}
}

# Stop any orphan Java processes listening on 8080 or 9090
foreach ($port in @(8080, 9090)) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        if ($c.OwningProcess -and $c.OwningProcess -ne 0) {
            try {
                Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
                Write-Host "Freed port $port (terminated PID $($c.OwningProcess))" -ForegroundColor Green
            } catch {}
        }
    }
}

if (Test-Path $runDir) {
    Remove-Item -Recurse -Force $runDir -ErrorAction SilentlyContinue
}

Write-Host "KeyFort demo environment stopped and cleaned." -ForegroundColor Cyan
