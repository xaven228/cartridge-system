Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"

if (-not (Test-Path (Join-Path $RootDir ".env"))) {
    Write-Host "WARNING: .env not found. Using defaults from docker-compose."
}

$AppPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }

Write-Host "[1/2] Starting containers (postgres + backend)..."
docker compose -f $ComposeFile up -d --build | Out-Host

Write-Host "[2/2] Waiting for API on port $AppPort..."
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$AppPort/api/departments" -TimeoutSec 2
        if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
}

if (-not $ready) {
    Write-Host "Backend did not become ready in time. Check logs:"
    Write-Host "  docker compose -f `"$ComposeFile`" logs backend"
    exit 1
}

Write-Host "Backend is up: http://localhost:$AppPort"
