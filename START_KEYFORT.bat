@echo off
title KeyFort Server (Port 8080)
cd /d "%~dp0"

echo ========================================================
echo            KeyFort Zero-Knowledge Platform
echo ========================================================
echo.
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
