Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"
$ProjectName = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { "cartridge-system" }
$VolumeName = "${ProjectName}_postgres_data"
$BackendImage = "cartridge-system-backend:latest"

Write-Host "== Cartridge System Status =="
Write-Host ""

Write-Host "[1] Containers"
try {
    docker compose -f $ComposeFile ps | Out-Host
} catch {
    Write-Host "  Failed to read container status."
}
Write-Host ""

Write-Host "[2] Backend image"
try {
    $imgId = (docker image inspect -f "{{.Id}}" $BackendImage 2>$null).Trim()
    if ($imgId) {
        $imgCreated = (docker image inspect -f "{{.Created}}" $BackendImage 2>$null).Trim()
        Write-Host "  Image: $BackendImage"
        Write-Host "  Id: $imgId"
        Write-Host "  Created: $imgCreated"
    } else {
        Write-Host "  Image '$BackendImage' not found."
    }
} catch {
    Write-Host "  Image '$BackendImage' not found."
}
Write-Host ""

Write-Host "[3] Postgres volume size"
try {
    $exists = docker volume inspect $VolumeName 2>$null
    if ($LASTEXITCODE -eq 0) {
        docker run --rm -v "${VolumeName}:/data:ro" alpine:3.20 sh -lc "du -sh /data 2>/dev/null || true" | Out-Host
    } else {
        Write-Host "  Volume '$VolumeName' not found."
    }
} catch {
    Write-Host "  Volume '$VolumeName' not found."
}
Write-Host ""

Write-Host "[4] Recent backend errors (last 200 lines)"
try {
    $backendPs = docker compose -f $ComposeFile ps backend --status running 2>$null
    if ($backendPs -match "backend") {
        $logs = docker compose -f $ComposeFile logs --tail 200 backend 2>$null
        $matches = $logs | Select-String -Pattern "ERROR|Exception|Caused by"
        if ($matches) {
            $matches | ForEach-Object { Write-Host $_.Line }
        } else {
            Write-Host "  No ERROR/Exception lines found."
        }
    } else {
        Write-Host "  Backend container is not running."
    }
} catch {
    Write-Host "  Unable to read backend logs."
}
