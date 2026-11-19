@echo off
cd /d "%~dp0"
echo ========================================================
echo Starting KeyFort Server on http://localhost:8080 ...
echo ========================================================
call gradlew.bat :keyfort-server:run
pause
