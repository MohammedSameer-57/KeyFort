@echo off
title KeyFort Server (Port 8080)
cd /d "%~dp0"

echo ========================================================
echo            KeyFort Zero-Knowledge Platform
echo ========================================================
echo.

:: Free port 8080 if an old process was left running
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr :8080 ^| findstr LISTENING') do (
    echo [INFO] Freeing port 8080 from previous process (PID: %%a)...
    taskkill /f /pid %%a >nul 2>&1
)

echo Starting KeyFort Backend Server on http://localhost:8080 ...
echo.
echo Once you see "Started KeyFortServerApplication", open this in your browser:
echo http://localhost:8080/ledger/verify
echo.
echo [Press Ctrl+C at any time to stop the server]
echo --------------------------------------------------------
echo.

call gradlew.bat :keyfort-server:run

echo.
echo Server stopped.
pause
