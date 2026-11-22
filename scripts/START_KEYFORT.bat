@echo off
cd /d "%~dp0.."
echo Starting KeyFort server on http://localhost:8080...
call gradlew.bat :keyfort-server:run
