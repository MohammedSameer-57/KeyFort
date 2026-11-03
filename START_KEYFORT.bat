@echo off
setlocal enabledelayedexpansion
title KeyFort - One-Click Launcher
echo ========================================================
echo            KeyFort Zero-Knowledge Password Manager
echo                   One-Click Launcher
echo ========================================================
echo.

cd /d "%~dp0"

set "ADB=C:\Users\Rachit.000\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "EMULATOR=C:\Users\Rachit.000\AppData\Local\Android\Sdk\emulator\emulator.exe"

:: 1. Check if KeyFort Server is running
echo [1/3] Checking KeyFort Backend Server (port 8080)...
netstat -ano | findstr :8080 >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo       [OK] Backend server is already running on http://localhost:8080
) else (
    echo       [..] Starting backend server in background...
    start /min "KeyFort-Server" cmd /c "gradlew.bat :keyfort-server:run"
    powershell -nop -c "Start-Sleep -Seconds 6"
    echo       [OK] Backend server started.
)

:: 2. Check if Android Emulator is running
echo.
echo [2/3] Checking Android Emulator...
"%ADB%" devices | findstr "emulator-5554" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo       [OK] Emulator is already running.
) else (
    echo       [..] Starting emulator (medium_phone)...
    start "" "%EMULATOR%" -avd medium_phone -no-boot-anim
    echo       [..] Waiting for device to finish booting...
    "%ADB%" wait-for-device
    powershell -nop -c "while ((& '%ADB%' shell getprop sys.boot_completed).Trim() -ne '1') { Start-Sleep -Seconds 2 }"
    echo       [OK] Emulator booted successfully.
)

:: 3. Launch KeyFort App
echo.
echo [3/3] Launching KeyFort Android App...
"%ADB%" shell am start -n org.keyfort.android/.ui.UnlockActivity >nul 2>&1
echo       [OK] KeyFort is now open on your emulator screen!
echo.
echo ========================================================
echo   KeyFort is LIVE and ready to use!
echo   - App: Running on your emulator window
echo   - Backend: http://localhost:8080
echo   - Ledger Verify: http://localhost:8080/ledger/verify
echo ========================================================
echo.
pause
