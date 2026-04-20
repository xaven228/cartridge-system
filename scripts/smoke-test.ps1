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
$HealthUsername = if ($env:APP_HEALTHCHECK_USERNAME) { $env:APP_HEALTHCHECK_USERNAME } else { "admin" }
$HealthPassword = if ($env:APP_HEALTHCHECK_PASSWORD) { $env:APP_HEALTHCHECK_PASSWORD } else { "00000" }
$BaseUrl = "http://localhost:$AppPort"
$FrontendUrl = "http://localhost:$FrontendPort"
$Status = 0
$MaxAttempts = 15
$SleepSeconds = 2
$BackendToken = ""
$FrontendToken = ""

function Test-Endpoint {
    param([string]$Path)
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $headers = @{ Authorization = "Bearer $script:BackendToken" }
            $resp = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$Path" -Headers $headers -TimeoutSec 5
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
            $headers = @{}
            if ($script:FrontendToken) {
                $headers.Authorization = "Bearer $script:FrontendToken"
            }
            $resp = Invoke-WebRequest -UseBasicParsing -Uri "$FrontendUrl$Path" -Headers $headers -TimeoutSec 5
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

function Invoke-BackendLogin {
    $body = @{
        username = $HealthUsername
        password = $HealthPassword
    } | ConvertTo-Json -Compress
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 5
        if ($resp.StatusCode -eq 200) {
            $json = $resp.Content | ConvertFrom-Json
            $script:BackendToken = $json.token
            if ($script:BackendToken) {
                Write-Host "  OK  POST /api/auth/login -> 200"
                return
            }
        }
    } catch {
    }
    Write-Host "  FAIL POST /api/auth/login -> request failed"
    $script:Status = 1
}

function Invoke-FrontendLogin {
    $body = @{
        username = $HealthUsername
        password = $HealthPassword
    } | ConvertTo-Json -Compress
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "$FrontendUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 5
        if ($resp.StatusCode -eq 200) {
            $json = $resp.Content | ConvertFrom-Json
            $script:FrontendToken = $json.token
            if ($script:FrontendToken) {
                Write-Host "  OK  POST $FrontendUrl/api/auth/login -> 200"
                return
            }
        }
    } catch {
    }
    Write-Host "  FAIL POST $FrontendUrl/api/auth/login -> request failed"
    $script:Status = 1
}

Write-Host "== Cartridge System Smoke Test =="
Write-Host "Base URL: $BaseUrl"
Write-Host "Frontend URL: $FrontendUrl"
Write-Host ""

Invoke-FrontendLogin
Test-FrontendEndpoint ""
Test-FrontendEndpoint "/api/auth/me"
Invoke-BackendLogin
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
