@echo off
title KeyFort - Share to Mobile (Live Public Tunnel)
cls
echo ============================================================
echo   KeyFort: Share Live Web App to Your Phone or Friends
echo ============================================================
echo.
echo Make sure START_KEYFORT.bat is running in another window!
echo.
echo Creating an instant, secure public HTTPS link for port 8080...
echo (No accounts or installation required)
echo.
echo ------------------------------------------------------------
echo Once connected, open the generated HTTPS link on your phone!
echo Press Ctrl+C at any time to stop sharing.
echo ------------------------------------------------------------
echo.

ssh -o StrictHostKeyChecking=no -R 80:localhost:8080 nokey@localhost.run
pause
