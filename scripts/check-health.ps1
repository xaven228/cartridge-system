Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RootDir "docker-compose.yml"

if (Test-Path (Join-Path $RootDir ".env")) {
    Get-Content (Join-Path $RootDir ".env") | ForEach-Object {
        if ($_ -match "^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2])
        }
    }
}

$AppPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
$PostgresHostPort = if ($env:POSTGRES_HOST_PORT) { $env:POSTGRES_HOST_PORT } else { "5433" }
$Status = 0

Write-Host "== Cartridge System Health Check =="

Write-Host ""
Write-Host "[1] Docker containers"
try {
    docker compose -f $ComposeFile ps | Out-Host
} catch {
    Write-Host "  Failed to read docker compose status."
    $Status = 1
}

Write-Host ""
Write-Host "[2] API check"
try {
    $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$AppPort/api/departments" -TimeoutSec 5
    if ($resp.StatusCode -eq 200) {
        Write-Host "  OK: API responds on port $AppPort (HTTP 200)"
    } else {
        Write-Host "  FAIL: API returned HTTP $($resp.StatusCode)"
        $Status = 1
    }
} catch {
    Write-Host "  FAIL: API check failed on port $AppPort"
    $Status = 1
}

Write-Host ""
Write-Host "[3] TCP ports"
try {
    $appListen = Get-NetTCPConnection -State Listen -LocalPort ([int]$AppPort) -ErrorAction SilentlyContinue
    if ($appListen) {
        Write-Host "  OK: app port $AppPort is listening"
    } else {
        Write-Host "  FAIL: app port $AppPort is not listening"
        $Status = 1
    }
} catch {
    Write-Host "  WARN: unable to check app port via Get-NetTCPConnection"
}

try {
    $pgListen = Get-NetTCPConnection -State Listen -LocalPort ([int]$PostgresHostPort) -ErrorAction SilentlyContinue
    if ($pgListen) {
        Write-Host "  OK: postgres host port $PostgresHostPort is listening"
    } else {
        Write-Host "  FAIL: postgres host port $PostgresHostPort is not listening"
        $Status = 1
    }
} catch {
    Write-Host "  WARN: unable to check postgres port via Get-NetTCPConnection"
}

Write-Host ""
Write-Host "[4] Disk usage"
Get-PSDrive -PSProvider FileSystem | Select-Object Name, @{Name='UsedGB';Expression={[math]::Round(($_.Used/1GB),2)}}, @{Name='FreeGB';Expression={[math]::Round(($_.Free/1GB),2)}} | Out-Host

Write-Host ""
if ($Status -eq 0) {
    Write-Host "Result: HEALTHY"
} else {
    Write-Host "Result: UNHEALTHY"
}

exit $Status
