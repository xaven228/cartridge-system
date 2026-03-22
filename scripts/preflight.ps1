Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$EnvFile = Join-Path $RootDir ".env"
$ComposeFile = Join-Path $RootDir "docker-compose.yml"
$Status = 0

Write-Host "== Cartridge System Preflight =="
Write-Host ""

Write-Host "[1] Docker CLI"
try {
    docker --version | Out-Host
} catch {
    Write-Host "  FAIL: docker command not found"
    $Status = 1
}
Write-Host ""

Write-Host "[2] Docker daemon"
try {
    docker info | Out-Null
    Write-Host "  OK: Docker daemon is reachable"
} catch {
    Write-Host "  FAIL: Docker daemon is not reachable"
    $Status = 1
}
Write-Host ""

Write-Host "[3] Docker Compose"
try {
    docker compose version | Out-Host
} catch {
    Write-Host "  FAIL: docker compose is not available"
    $Status = 1
}
Write-Host ""

Write-Host "[4] Environment file"
if (Test-Path $EnvFile) {
    Write-Host "  OK: .env found at $EnvFile"
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match "^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2])
        }
    }
} else {
    Write-Host "  FAIL: .env not found (create from .env.example)"
    $Status = 1
}
Write-Host ""

$AppPort = if ($env:APP_PORT) { [int]$env:APP_PORT } else { 8080 }
$PostgresHostPort = if ($env:POSTGRES_HOST_PORT) { [int]$env:POSTGRES_HOST_PORT } else { 5433 }

Write-Host "[5] Host ports currently in use"
try {
    $appListen = Get-NetTCPConnection -State Listen -LocalPort $AppPort -ErrorAction SilentlyContinue
    if ($appListen) {
        Write-Host "  WARN: APP_PORT $AppPort is already in use"
    } else {
        Write-Host "  OK: APP_PORT $AppPort is free"
    }
} catch {
    Write-Host "  WARN: unable to check APP_PORT via Get-NetTCPConnection"
}

try {
    $pgListen = Get-NetTCPConnection -State Listen -LocalPort $PostgresHostPort -ErrorAction SilentlyContinue
    if ($pgListen) {
        Write-Host "  WARN: POSTGRES_HOST_PORT $PostgresHostPort is already in use"
    } else {
        Write-Host "  OK: POSTGRES_HOST_PORT $PostgresHostPort is free"
    }
} catch {
    Write-Host "  WARN: unable to check POSTGRES_HOST_PORT via Get-NetTCPConnection"
}
Write-Host ""

Write-Host "[6] Compose config validation"
try {
    docker compose -f $ComposeFile config | Out-Null
    Write-Host "  OK: docker-compose.yml is valid"
} catch {
    Write-Host "  FAIL: docker-compose.yml validation failed"
    $Status = 1
}
Write-Host ""

if ($Status -eq 0) {
    Write-Host "Result: READY"
} else {
    Write-Host "Result: NOT READY"
}

exit $Status
