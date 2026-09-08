@echo off
setlocal enabledelayedexpansion

echo ==================================================
echo         SwapnoPay Enterprise Deployment Tool
echo ==================================================
echo.

:: 1. Verify Supabase CLI is available
where supabase >nul 2>nul
if %errorLevel% neq 0 (
    echo [ERROR] Supabase CLI was not found on your system path.
    echo Please install it globally: npm install -g supabase
    pause
    exit /b 1
)

echo [INFO] Supabase CLI found. Ready for deployment.
echo.

:: 2. Prompt for Supabase Reference ID
set /p "PROJECT_REF=Enter your Supabase Project Reference ID (e.g. abc123xyz): "
if "%PROJECT_REF%"=="" (
    echo [ERROR] Project reference cannot be empty.
    pause
    exit /b 1
)

echo.
echo [INFO] Linking to Supabase Project: %PROJECT_REF%...
call supabase link --project-ref %PROJECT_REF%
if %errorLevel% neq 0 (
    echo [ERROR] Failed to link project. Verify credentials and try again.
    pause
    exit /b 1
)

echo.
echo [INFO] Deploying Database Schema Migrations...
call supabase db push
if %errorLevel% neq 0 (
    echo [ERROR] Migration deployment failed.
    pause
    exit /b 1
)

echo.
echo [INFO] Deploying Deno Serverless Edge Functions...
call supabase functions deploy process-sms --no-verify-jwt
call supabase functions deploy create-order --no-verify-jwt
call supabase functions deploy resolve-appeal --no-verify-jwt

if %errorLevel% neq 0 (
    echo [ERROR] Edge functions deployment failed.
    pause
    exit /b 1
)

echo.
echo ==================================================
echo [SUCCESS] SwapnoPay Project successfully deployed!
echo ==================================================
pause
exit /b 0
