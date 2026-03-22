Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"

Write-Host "Stopping and removing containers..."
docker compose -f $ComposeFile down | Out-Host
Write-Host "Done."
