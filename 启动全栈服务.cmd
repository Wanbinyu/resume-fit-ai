@echo off
setlocal
cd /d "%~dp0"
title Resume Fit AI - Full Stack

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js 22.3+ is required.
  pause
  exit /b 1
)

set "BACKEND_COMMAND=mvn spring-boot:run"
where mvn >nul 2>nul
if errorlevel 1 (
  where java >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] Maven or Java is required for the Spring Boot backend.
    pause
    exit /b 1
  )
  if not exist "backend\target\resume-fit-backend-0.1.0.jar" (
    echo [ERROR] Maven is unavailable and the backend jar was not found.
    echo Install Maven 3.9+ or build backend\target\resume-fit-backend-0.1.0.jar first.
    pause
    exit /b 1
  )
  set "BACKEND_COMMAND=java -jar resume-fit-backend-0.1.0.jar"
  echo [INFO] Maven was not found. Using the existing backend jar.
)

if not exist "node_modules\express\package.json" (
  echo [INFO] Installing Node.js dependencies...
  call npm install
  if errorlevel 1 (
    echo [ERROR] Node.js dependency installation failed.
    pause
    exit /b 1
  )
)

echo [INFO] Starting Spring Boot backend on http://localhost:8080 ...
start "Resume Fit AI Backend" /D "%~dp0backend" cmd /k %BACKEND_COMMAND%

echo [INFO] Starting Node.js frontend on http://localhost:3000 ...
start "Resume Fit AI Frontend" /D "%~dp0" cmd /k npm start

echo.
echo [INFO] Services are starting.
echo [INFO] Frontend: http://localhost:3000
echo [INFO] Backend:  http://localhost:8080/
echo [INFO] Workspace: http://localhost:8080/workspace.html
echo.
pause
