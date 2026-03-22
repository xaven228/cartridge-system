Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if (Test-Path (Join-Path $RootDir ".env")) {
    Get-Content (Join-Path $RootDir ".env") | ForEach-Object {
        if ($_ -match "^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2])
        }
    }
}

$AppPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
$FrontendPort = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { "3000" }
$BaseUrl = "http://localhost:$AppPort"
$FrontendUrl = "http://localhost:$FrontendPort"
$Status = 0
$MaxAttempts = 15
$SleepSeconds = 2

function Test-Endpoint {
    param([string]$Path)
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$Path" -TimeoutSec 5
            if ($resp.StatusCode -eq 200) {
                Write-Host "  OK  GET $Path -> 200"
                return
            }
        } catch {
        }
        Start-Sleep -Seconds $SleepSeconds
    }
    Write-Host "  FAIL GET $Path -> request failed"
    $script:Status = 1
}

function Test-FrontendEndpoint {
    param([string]$Path)
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri "$FrontendUrl$Path" -TimeoutSec 5
            if ($resp.StatusCode -eq 200) {
                Write-Host "  OK  GET $FrontendUrl$Path -> 200"
                return
            }
        } catch {
        }
        Start-Sleep -Seconds $SleepSeconds
    }
    Write-Host "  FAIL GET $FrontendUrl$Path -> request failed"
    $script:Status = 1
}

Write-Host "== Cartridge System Smoke Test =="
Write-Host "Base URL: $BaseUrl"
Write-Host "Frontend URL: $FrontendUrl"
Write-Host ""

Test-FrontendEndpoint ""
Test-FrontendEndpoint "/api/departments"
Test-Endpoint "/api/departments"
Test-Endpoint "/api/cartridge-models"
Test-Endpoint "/api/cartridges"
Test-Endpoint "/api/refill-history/cartridge/1"

Write-Host ""
if ($Status -eq 0) {
    Write-Host "Result: PASS"
} else {
    Write-Host "Result: FAIL"
}

exit $Status
