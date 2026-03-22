Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"
$AppPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }

Write-Host "[1/3] Stopping containers and removing database volume..."
docker compose -f $ComposeFile down -v | Out-Host

Write-Host "[2/3] Starting fresh stack..."
docker compose -f $ComposeFile up -d --build | Out-Host

Write-Host "[3/3] Waiting for API..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$AppPort/api/departments" -TimeoutSec 3
        if ($resp.StatusCode -eq 200) {
            Write-Host "Database reset complete. API is ready."
            exit 0
        }
    } catch {
    }
    Start-Sleep -Seconds 1
}

Write-Host "API did not become ready in time. Check logs:"
Write-Host "  docker compose -f `"$ComposeFile`" logs backend"
exit 1
