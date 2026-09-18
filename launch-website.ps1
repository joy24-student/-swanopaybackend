# SwapnoPay 1-Click Platform & Website Launcher (PowerShell)
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "        🚀 SwapnoPay 1-Click Platform & Website Launcher        " -ForegroundColor Yellow
Write-Host "================================================================" -ForegroundColor Cyan

$baseDir = $PSScriptRoot

# 1. Verify Node.js
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Node.js is not installed or not found in PATH." -ForegroundColor Red
    Write-Host "Please install Node.js (LTS) from https://nodejs.org" -ForegroundColor Red
    Pause
    Exit 1
}

Write-Host "[✓] Node.js version: $(node -v)" -ForegroundColor Green

# 2. Check dependencies
if (-not (Test-Path "$baseDir\swapnopay-backend\node_modules")) {
    Write-Host "[INFO] Installing backend dependencies..." -ForegroundColor Gray
    Start-Process -FilePath "npm.cmd" -ArgumentList "install" -WorkingDirectory "$baseDir\swapnopay-backend" -Wait -NoNewWindow
}
if (-not (Test-Path "$baseDir\web\node_modules")) {
    Write-Host "[INFO] Installing web frontend dependencies..." -ForegroundColor Gray
    Start-Process -FilePath "npm.cmd" -ArgumentList "install" -WorkingDirectory "$baseDir\web" -Wait -NoNewWindow
}
if (-not (Test-Path "$baseDir\admin\node_modules")) {
    Write-Host "[INFO] Installing admin panel dependencies..." -ForegroundColor Gray
    Start-Process -FilePath "npm.cmd" -ArgumentList "install" -WorkingDirectory "$baseDir\admin" -Wait -NoNewWindow
}

# 3. Launch services in separate processes
Write-Host "[INFO] Starting Backend API on port 4000..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\swapnopay-backend'; node src/index.js" -WindowStyle Minimized

Write-Host "[INFO] Starting Web Server on port 3000..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\web'; node server.js" -WindowStyle Minimized

Write-Host "[INFO] Starting Admin Dev Server on port 5173..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\admin'; npm run dev -- --port 5173" -WindowStyle Minimized

# 4. Wait for initialization & open browser
Write-Host "[INFO] Initializing services..." -ForegroundColor Gray
Start-Sleep -Seconds 2

Write-Host "[✓] Opening Website in default browser..." -ForegroundColor Green
Start-Process "http://localhost:3000"

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  🎉 ALL SERVICES ARE LIVE AND READY!                           " -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  • Website & Landing Page : http://localhost:3000"
Write-Host "  • Checkout Widget Demo   : http://localhost:3000/widget.html"
Write-Host "  • Merchant Web Portal    : http://localhost:3000/portal.html"
Write-Host "  • Developer Docs         : http://localhost:3000/docs.html"
Write-Host "  • Dynamic Payment Forms  : http://localhost:3000/form.html"
Write-Host "  • Admin Control Panel    : http://localhost:5173"
Write-Host "  • Backend API Health     : http://localhost:4000/healthz"
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Press Enter to exit this launcher window (services keep running)."
Read-Host
