@echo off
title SwapnoPay Platform Launcher
setlocal enabledelayedexpansion

echo ================================================================
echo         🚀 SwapnoPay 1-Click Platform & Website Launcher
echo ================================================================
echo.

:: 1. Verify Node.js
where node >nul 2>nul
if %errorLevel% neq 0 (
    echo [ERROR] Node.js is not installed or not in PATH!
    echo Please download and install Node.js (LTS) from https://nodejs.org
    echo.
    pause
    exit /b 1
)

echo [✓] Node.js found:
node -v
echo.

:: 2. Check and install backend dependencies if missing
if not exist "%~dp0swapnopay-backend\node_modules" (
    echo [INFO] Installing backend dependencies...
    cd /d "%~dp0swapnopay-backend"
    call npm install
)

:: 3. Check and install web frontend dependencies if missing
if not exist "%~dp0web\node_modules" (
    echo [INFO] Installing web frontend dependencies...
    cd /d "%~dp0web"
    call npm install
)

:: 4. Check and install admin dependencies if missing
if not exist "%~dp0admin\node_modules" (
    echo [INFO] Installing admin dependencies...
    cd /d "%~dp0admin"
    call npm install
)

echo [INFO] Starting SwapnoPay Central Backend API on port 4000...
start "SwapnoPay Backend (Port 4000)" cmd /k "cd /d %~dp0swapnopay-backend && node src/index.js"

echo [INFO] Starting SwapnoPay Web Server on port 3000...
start "SwapnoPay Web Frontend (Port 3000)" cmd /k "cd /d %~dp0web && node server.js"

echo [INFO] Starting SwapnoPay Admin Panel on port 5173...
start "SwapnoPay Admin Panel (Port 5173)" cmd /k "cd /d %~dp0admin && npm run dev -- --port 5173"

echo.
echo [INFO] Waiting for servers to initialize...
timeout /t 3 /nobreak >nul

echo [✓] Opening SwapnoPay Website in default browser...
start http://localhost:3000

echo.
echo ================================================================
echo   🎉 ALL SERVICES LAUNCHED SUCCESSFULLY!
echo ================================================================
echo   • Website & Landing Page : http://localhost:3000
echo   • Checkout Widget Demo   : http://localhost:3000/widget.html
echo   • Merchant Web Portal    : http://localhost:3000/portal.html
echo   • Developer Docs         : http://localhost:3000/docs.html
echo   • Dynamic Payment Forms  : http://localhost:3000/form.html
echo   • Admin Control Panel    : http://localhost:5173
echo   • Backend API Healthz    : http://localhost:4000/healthz
echo ================================================================
echo Press any key to exit this launcher window (services keep running).
pause >nul
