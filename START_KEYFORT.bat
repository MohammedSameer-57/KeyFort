@echo off
setlocal enabledelayedexpansion
title KeyFort - Quick Launcher
echo ========================================================
echo            KeyFort Zero-Knowledge Platform
echo                   Universal Launcher
echo ========================================================
echo.

cd /d "%~dp0"

:: 1. Check if Java is installed
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if "%JAVA_HOME%"=="" (
        echo [ERROR] Java 17+ is required but not found on your system!
        echo.
        echo Please install Java 17 or higher:
        echo 1. Download OpenJDK 17: https://adoptium.net/temurin/releases/?version=17
        echo 2. Run the installer (it takes 1 minute).
        echo.
        pause
        exit /b 1
    )
)
echo [OK] Java detected.

:: 2. Find Android ADB and Emulator dynamically
set "ADB=adb"
where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    )
)

:: 3. Start KeyFort Backend Server (Spring Boot on port 8080)
echo.
echo [1/2] Starting KeyFort Backend Server (port 8080)...
netstat -ano | findstr :8080 >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo       [OK] Server is already running on http://localhost:8080
) else (
    echo       [..] Starting server in background...
    start /min "KeyFort-Server" cmd /c "gradlew.bat :keyfort-server:run"
    timeout /t 6 /nobreak >nul
    echo       [OK] Backend server started.
)

:: 4. Android Emulator / Device Check
echo.
echo [2/2] Checking Android device / emulator...
"%ADB%" devices 2>nul | findstr "device$" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo       [OK] Active Android device detected. Launching app...
    "%ADB%" shell am start -n org.keyfort.android/.ui.UnlockActivity >nul 2>&1
    echo       [OK] KeyFort app launched on device screen.
) else (
    echo       [INFO] No running Android emulator found.
    echo       (To run mobile app: open your emulator in Android Studio and run this again).
)

echo.
echo ========================================================
echo   KeyFort Backend is LIVE!
echo   - Backend URL  : http://localhost:8080
echo   - Audit Ledger : http://localhost:8080/ledger/verify
echo ========================================================
echo.
echo Opening audit ledger in your browser...
start http://localhost:8080/ledger/verify
pause
