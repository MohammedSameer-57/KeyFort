@echo off
cd /d "%~dp0.."
set "HTML_PATH=%CD%\preview.html"

if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" (
    start "" "C:\Program Files\Google\Chrome\Application\chrome.exe" "%HTML_PATH%"
    exit /b 0
)

if exist "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" (
    start "" "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" "%HTML_PATH%"
    exit /b 0
)

start "" "%HTML_PATH%"
exit /b 0
