@echo off
setlocal
cd /d "%~dp0"
title Resume Fit AI

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js is not installed or is not available in PATH.
  echo Install Node.js 22.3 or newer, then run this launcher again.
  pause
  exit /b 1
)

node -e "const [major,minor]=process.versions.node.split('.').map(Number);process.exit(major>22||(major===22&&minor>=3)?0:1)"
if errorlevel 1 (
  echo [ERROR] Node.js 22.3 or newer is required.
  echo Current version:
  node --version
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm is not available in PATH.
  pause
  exit /b 1
)

if not exist "node_modules\express\package.json" (
  echo [INFO] Installing dependencies for the first run...
  call npm install
  if errorlevel 1 (
    echo [ERROR] Dependency installation failed.
    pause
    exit /b 1
  )
)

set "APP_PORT=3000"
for /f "delims=" %%P in ('node -e "require('dotenv').config({quiet:true});process.stdout.write(process.env.PORT||'3000')"') do set "APP_PORT=%%P"
set "APP_URL=http://localhost:%APP_PORT%"

powershell -NoProfile -Command "try { $health = Invoke-RestMethod 'http://127.0.0.1:%APP_PORT%/api/health' -TimeoutSec 2; if ($health.ok) { if ($env:RESUME_FIT_NO_BROWSER -ne '1') { Start-Process '%APP_URL%' }; exit 0 } } catch {}; exit 1"
if not errorlevel 1 (
  echo [INFO] Resume Fit AI is already running. Opened it in your browser.
  exit /b 0
)

echo [INFO] Starting Resume Fit AI...
echo [INFO] Keep this window open. Close it or press Ctrl+C to stop the server.

if not "%RESUME_FIT_NO_BROWSER%"=="1" start "" /min powershell -NoProfile -WindowStyle Hidden -Command "$url='%APP_URL%'; for ($i=0; $i -lt 60; $i++) { try { $health=Invoke-RestMethod ($url + '/api/health') -TimeoutSec 2; if ($health.ok) { Start-Process $url; exit 0 } } catch {}; Start-Sleep -Milliseconds 500 }; exit 1"

call npm start

echo.
echo [INFO] Resume Fit AI has stopped.
pause
