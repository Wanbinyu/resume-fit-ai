@echo off
setlocal
cd /d "%~dp0"
cd /d "%~dp0backend"
if not exist "target\resume-fit-backend-0.1.0.jar" (
  echo [ERROR] Build the backend first: mvn package
  pause
  exit /b 1
)
echo [INFO] DeepSeek Agent workspace: http://localhost:8080/workspace.html
echo [INFO] Reads the existing root .env. Use --app.ai.demo-mode=true for offline Demo.
java -jar target\resume-fit-backend-0.1.0.jar --app.ai.demo-mode=false "--spring.datasource.url=jdbc:h2:file:./data/agent-workspace;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE" %*
pause
