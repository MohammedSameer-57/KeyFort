@echo off
setlocal enabledelayedexpansion
title KeyFort - Quick Launcher
echo ========================================================
echo            KeyFort Zero-Knowledge Platform
echo                   Universal Launcher
echo ========================================================
echo.

cd /d "%~dp0"

:: 1. Auto-configure Java 17 if JAVA_HOME is missing
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
        set "PATH=!JAVA_HOME!\bin;!PATH!"
    ) else if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
        set "PATH=!JAVA_HOME!\bin;!PATH!"
    )
)

:: Verify Java
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java 17+ is required but not detected!
    echo Please install Adoptium OpenJDK 17: https://adoptium.net/
    echo.
    pause
    exit /b 1
)
echo [OK] Java 17 detected.

:: 2. Check if Server is already running on port 8080
netstat -ano | findstr :8080 | findstr LISTENING >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [OK] Server is already running on http://localhost:8080
    goto open_browser
)

:: 3. Start Backend Server in a new window
echo [..] Starting KeyFort Backend Server...
start "KeyFort Backend Server (Port 8080)" cmd /k "gradlew.bat :keyfort-server:run"

:: 4. Wait for server to become responsive
echo [..] Waiting for server to initialize...
powershell -NoProfile -Command "$ready = $false; for ($i=0; $i -lt 30; $i++) { Start-Sleep -Seconds 1; try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/ledger/verify' -TimeoutSec 1 -ErrorAction Stop; if ($r.valid -eq $true) { $ready = $true; break } } catch {} }; if (-not $ready) { exit 1 }"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] Server is taking longer than usual to start.
    echo     Please check the "KeyFort Backend Server" terminal window.
    echo.
) else (
    echo [OK] KeyFort Backend Server is LIVE!
)

:open_browser
echo.
echo ========================================================
echo   KeyFort is LIVE and ready!
echo   - Backend URL  : http://localhost:8080
echo   - Audit Ledger : http://localhost:8080/ledger/verify
echo ========================================================
echo.
echo Opening audit ledger in your browser...
start http://localhost:8080/ledger/verify
pause
