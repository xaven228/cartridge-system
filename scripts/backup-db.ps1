Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"
$BackupDir = Join-Path $RootDir "backups"

if (Test-Path (Join-Path $RootDir ".env")) {
    Get-Content (Join-Path $RootDir ".env") | ForEach-Object {
        if ($_ -match "^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2])
        }
    }
}

$PostgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "cartridge_db" }
$PostgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "cartridge_user" }
$RetentionDays = if ($env:BACKUP_RETENTION_DAYS) { [int]$env:BACKUP_RETENTION_DAYS } else { 14 }

New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutFile = Join-Path $BackupDir "cartridge_db-$Timestamp.sql"

Write-Host "Creating backup: $OutFile"
docker compose -f $ComposeFile exec -T postgres pg_dump -U $PostgresUser -d $PostgresDb | Set-Content -Path $OutFile
Write-Host "Backup created: $OutFile"

Write-Host "Applying retention policy: keep last $RetentionDays days..."
$Threshold = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupDir -File -Filter "cartridge_db-*.sql" |
    Where-Object { $_.LastWriteTime -lt $Threshold } |
    ForEach-Object {
        Write-Host "Removing old backup: $($_.FullName)"
        Remove-Item -Path $_.FullName -Force
    }
Write-Host "Retention check complete."
