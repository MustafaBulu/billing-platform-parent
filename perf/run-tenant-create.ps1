$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path "perf/results" | Out-Null

Write-Output "Building tenant-service jar for docker image..."
./mvnw -q -DskipTests -pl tenant-service -am package

docker compose -f docker-compose.local.yml up -d --build mongo tenant-service

function Wait-Ready {
  param(
    [string]$Name,
    [string]$Url
  )

  $deadline = (Get-Date).AddMinutes(3)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
        Write-Output "$Name is ready: $Url"
        return
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  }

  throw "$Name did not become ready in time: $Url"
}

Wait-Ready -Name "tenant-service" -Url "http://localhost:8081/actuator/health/readiness"

$env:PERF_AUTH_TOKEN = if ($env:PERF_AUTH_TOKEN) { $env:PERF_AUTH_TOKEN } else { "dev-admin-token" }
$env:TARGET_P95_MS = if ($env:TARGET_P95_MS) { $env:TARGET_P95_MS } else { "300" }
$env:TARGET_ERROR_RATE = if ($env:TARGET_ERROR_RATE) { $env:TARGET_ERROR_RATE } else { "0.01" }
$env:PERF_VUS = if ($env:PERF_VUS) { $env:PERF_VUS } else { "10" }
$env:PERF_DURATION = if ($env:PERF_DURATION) { $env:PERF_DURATION } else { "1m" }
$env:TENANT_BASE_URL = if ($env:TENANT_BASE_URL) { $env:TENANT_BASE_URL } else { "http://tenant-service:8081" }

docker run --rm `
  --network billing-platform-parent_default `
  -v "${PWD}:/work" `
  -w /work `
  -e PERF_AUTH_TOKEN `
  -e TARGET_P95_MS `
  -e TARGET_ERROR_RATE `
  -e PERF_VUS `
  -e PERF_DURATION `
  -e TENANT_BASE_URL `
  grafana/k6:0.52.0 `
  run `
  --summary-export /work/perf/results/k6-tenant-create-summary.json `
  /work/perf/k6/scenarios/tenant-create.js

docker compose -f docker-compose.local.yml logs --no-color tenant-service | Out-File -Encoding utf8 "perf/results/tenant-service.log"

Write-Output "Tenant create perf test completed. Summary: perf/results/k6-tenant-create-summary.json"
