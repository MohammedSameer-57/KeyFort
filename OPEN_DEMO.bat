@echo off
title KeyFort - Live Presentation Demo
cls

echo ============================================================
echo   Launching KeyFort Live Demo in your Browser...
echo ============================================================
echo.

set "HTML_PATH=%~dp0preview.html"

if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" (
    start "" "C:\Program Files\Google\Chrome\Application\chrome.exe" "%HTML_PATH%"
    goto done
)

if exist "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" (
    start "" "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" "%HTML_PATH%"
    goto done
)

start "" "%HTML_PATH%"

:done
echo Opened successfully!
exit
