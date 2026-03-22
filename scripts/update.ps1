Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"
$HealthScript = Join-Path $RootDir "scripts/check-health.ps1"
$SmokeScript = Join-Path $RootDir "scripts/smoke-test.ps1"
$AppImage = "cartridge-system-backend:latest"
$FrontendImage = "cartridge-system-frontend:latest"

$PrevImageId = ""
$PrevFrontendImageId = ""
try {
    $PrevImageId = (docker image inspect -f "{{.Id}}" $AppImage 2>$null).Trim()
} catch {
    $PrevImageId = ""
}
try {
    $PrevFrontendImageId = (docker image inspect -f "{{.Id}}" $FrontendImage 2>$null).Trim()
} catch {
    $PrevFrontendImageId = ""
}

Write-Host "[1/4] Building and starting updated containers..."
docker compose -f $ComposeFile up -d --build | Out-Host

Write-Host "[2/4] Running health check..."
& $HealthScript
if ($LASTEXITCODE -eq 0) {
    & $SmokeScript
}
if ($LASTEXITCODE -eq 0) {
    Write-Host "[3/4] Update successful."
    exit 0
}

Write-Host "[3/4] Health check failed after update."

if ([string]::IsNullOrWhiteSpace($PrevImageId) -or [string]::IsNullOrWhiteSpace($PrevFrontendImageId)) {
    Write-Host "[4/4] Rollback not possible: previous backend/frontend image not found."
    exit 1
}

Write-Host "[4/4] Rolling back backend and frontend images to previous version..."
docker tag $PrevImageId $AppImage | Out-Host
docker tag $PrevFrontendImageId $FrontendImage | Out-Host
docker compose -f $ComposeFile up -d --no-build --force-recreate backend frontend postgres | Out-Host

Write-Host "Running health check after rollback..."
& $HealthScript
if ($LASTEXITCODE -eq 0) {
    & $SmokeScript
}
if ($LASTEXITCODE -eq 0) {
    Write-Host "Rollback successful."
} else {
    Write-Host "Rollback attempted but health check is still failing."
}

exit 1
