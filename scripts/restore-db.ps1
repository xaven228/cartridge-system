param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"

if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

if (Test-Path (Join-Path $RootDir ".env")) {
    Get-Content (Join-Path $RootDir ".env") | ForEach-Object {
        if ($_ -match "^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2])
        }
    }
}

$PostgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "cartridge_db" }
$PostgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "cartridge_user" }

Write-Host "Restoring database '$PostgresDb' from '$BackupFile'..."
Get-Content -Raw -Path $BackupFile | docker compose -f $ComposeFile exec -T postgres psql -U $PostgresUser -d $PostgresDb
Write-Host "Restore completed."
