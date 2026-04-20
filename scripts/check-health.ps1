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
$FrontendPort = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { "3000" }
$HealthUsername = if ($env:APP_HEALTHCHECK_USERNAME) { $env:APP_HEALTHCHECK_USERNAME } else { "admin" }
$HealthPassword = if ($env:APP_HEALTHCHECK_PASSWORD) { $env:APP_HEALTHCHECK_PASSWORD } else { "00000" }
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
Write-Host "[2] Frontend check"
try {
    $frontResp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$FrontendPort" -TimeoutSec 5
    if ($frontResp.StatusCode -eq 200) {
        Write-Host "  OK: frontend responds on port $FrontendPort (HTTP 200)"
    } else {
        Write-Host "  FAIL: frontend returned HTTP $($frontResp.StatusCode)"
        $Status = 1
    }
} catch {
    Write-Host "  FAIL: frontend check failed on port $FrontendPort"
    $Status = 1
}

Write-Host ""
Write-Host "[3] API auth check"
try {
    $loginBody = @{
        username = $HealthUsername
        password = $HealthPassword
    } | ConvertTo-Json -Compress
    $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$AppPort/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 5
    $loginJson = $resp.Content | ConvertFrom-Json
    $token = $loginJson.token
    if ($resp.StatusCode -eq 200 -and $token) {
        Write-Host "  OK: login works on port $AppPort (HTTP 200)"
    } else {
        Write-Host "  FAIL: login returned HTTP $($resp.StatusCode)"
        $Status = 1
    }
} catch {
    Write-Host "  FAIL: login check failed on port $AppPort"
    $Status = 1
}

Write-Host ""
Write-Host "[4] Authenticated API check"
try {
    $headers = @{ Authorization = "Bearer $token" }
    $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$AppPort/api/auth/me" -Headers $headers -TimeoutSec 5
    if ($resp.StatusCode -eq 200) {
        Write-Host "  OK: authenticated API responds on port $AppPort (HTTP 200)"
    } else {
        Write-Host "  FAIL: authenticated API returned HTTP $($resp.StatusCode)"
        $Status = 1
    }
} catch {
    Write-Host "  FAIL: authenticated API check failed on port $AppPort"
    $Status = 1
}

Write-Host ""
Write-Host "[5] TCP ports"
try {
    $frontListen = Get-NetTCPConnection -State Listen -LocalPort ([int]$FrontendPort) -ErrorAction SilentlyContinue
    if ($frontListen) {
        Write-Host "  OK: frontend port $FrontendPort is listening"
    } else {
        Write-Host "  FAIL: frontend port $FrontendPort is not listening"
        $Status = 1
    }
} catch {
    Write-Host "  WARN: unable to check frontend port via Get-NetTCPConnection"
}

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
Write-Host "[6] Disk usage"
Get-PSDrive -PSProvider FileSystem | Select-Object Name, @{Name='UsedGB';Expression={[math]::Round(($_.Used/1GB),2)}}, @{Name='FreeGB';Expression={[math]::Round(($_.Free/1GB),2)}} | Out-Host

Write-Host ""
if ($Status -eq 0) {
    Write-Host "Result: HEALTHY"
} else {
    Write-Host "Result: UNHEALTHY"
}

exit $Status
