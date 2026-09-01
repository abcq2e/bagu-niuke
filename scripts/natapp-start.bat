@echo off
:: qian-ai-agent NATAPP Tunnel Startup
:: Local Port: 8123  ->  External: check natapp console output
echo ============================================
echo   NATAPP Tunnel - qian-ai-agent (port 8123)
echo ============================================
"C:\Users\huahu\natapp.exe" -config="C:\Users\huahu\config.ini"
pause
