<#
  Starts the whole app for local development (Windows PowerShell):
    .\run-local.ps1            start backend (http://localhost:8080) and frontend (http://localhost:4200)
    .\run-local.ps1 -Stop      stop both
    .\run-local.ps1 -Rebuild   force a fresh backend build before starting

  Requirements: JDK 17, Maven, Node 22+, and a local PostgreSQL with an empty "jobtracker" database.
  Settings are read from the .env file in this folder (copy .env.example first).
#>
param(
    [switch]$Stop,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$jar = Join-Path $backend 'target\jobtracker.jar'

function Stop-App {
    Get-CimInstance Win32_Process |
        Where-Object { ($_.Name -eq 'java.exe' -and $_.CommandLine -match 'jobtracker\.jar') -or
                       ($_.Name -eq 'node.exe' -and $_.CommandLine -match 'ng(\.js)?"?\s+serve') } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host "stopped $($_.Name) ($($_.ProcessId))" }
}

if ($Stop) { Stop-App; return }

if (-not (Test-Path (Join-Path $root '.env'))) {
    throw "Missing .env - copy .env.example to .env and fill it in first."
}

Stop-App   # avoid two copies fighting over the same ports

if ($Rebuild -or -not (Test-Path $jar)) {
    Write-Host 'Building backend...' -ForegroundColor Cyan
    Push-Location $backend
    mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Backend build failed' }
    Pop-Location
}

if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    Write-Host 'Installing frontend dependencies...' -ForegroundColor Cyan
    Push-Location $frontend
    npm install --no-audit --no-fund
    Pop-Location
}

Write-Host 'Starting backend on :8080 ...' -ForegroundColor Cyan
$log = Join-Path $backend 'target\run.log'
Start-Process -FilePath java -ArgumentList '-Xmx300m', '-jar', $jar -WorkingDirectory $backend `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden

Write-Host 'Starting frontend on :4200 ...' -ForegroundColor Cyan
Start-Process -FilePath cmd.exe -ArgumentList '/c', 'npx ng serve --port 4200 > ng-serve.log 2>&1' `
    -WorkingDirectory $frontend -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds(120)
$apiUp = $false; $webUp = $false
while ((Get-Date) -lt $deadline -and -not ($apiUp -and $webUp)) {
    Start-Sleep -Seconds 2
    if (-not $apiUp) { try { $apiUp = (Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 2).status -eq 'UP' } catch {} }
    if (-not $webUp) { try { $webUp = (Invoke-WebRequest http://localhost:4200 -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch {} }
}

if ($apiUp -and $webUp) {
    Write-Host ''
    Write-Host 'Ready:  http://localhost:4200' -ForegroundColor Green
    Write-Host 'Demo login:  demo@example.com / Demo@1234'
    Write-Host 'API docs:    http://localhost:8080/swagger-ui.html'
    Write-Host 'Stop with:   .\run-local.ps1 -Stop'
} else {
    Write-Host "Not ready in time. Backend up: $apiUp, frontend up: $webUp" -ForegroundColor Yellow
    Write-Host "Check backend\target\run.log and frontend\ng-serve.log"
}
