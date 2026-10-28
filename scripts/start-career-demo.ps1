param([ValidateRange(1024,65535)][int]$Port = 18080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { Join-Path $env:TEMP 'career-maven\apache-maven-3.9.11\bin\mvn.cmd' }
if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Maven 3.9+ is required. Add mvn.cmd to PATH, then run again.' }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaCandidate = Get-ChildItem -LiteralPath 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $javaCandidate) { throw 'Java 21 is required. Set JAVA_HOME, then run again.' }
    $env:JAVA_HOME = $javaCandidate.FullName
}
$env:RF_REAL_MODEL_TEST = 'false'
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "Port $Port is already in use. Existing services were left running."
    Write-Host "If this is the career preview, open http://127.0.0.1:$Port/job-assistant.html"
    return
}
Write-Host "Local offline preview: http://127.0.0.1:$Port/job-assistant.html"
Write-Host 'This isolated preview uses an in-memory database. Restarting clears preview accounts and data.'
Write-Host 'It does not read .env, call a model/search provider, or start the photo service.'
Push-Location -LiteralPath $backendRoot
try {
    $arguments = "--server.address=127.0.0.1 --server.port=$Port --spring.config.import= --app.ai.demo-mode=true --spring.datasource.url=jdbc:h2:mem:career_preview --spring.datasource.username=sa --spring.datasource.password= --spring.datasource.driver-class-name=org.h2.Driver --app.admin.bootstrap-username= --app.admin.bootstrap-password="
    & $mavenPath -q spring-boot:run "-Dspring-boot.run.arguments=$arguments"
    if ($LASTEXITCODE -ne 0) { throw 'Career preview startup failed. See Maven output above.' }
} finally { Pop-Location }
