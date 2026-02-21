$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path "perf/results" | Out-Null

Write-Output "Building service jars for docker images..."
./mvnw -q -DskipTests -pl tenant-service,usage-service,billing-service,invoice-batch-service,payment-service,settlement-service -am package

docker compose -f docker-compose.local.yml up -d --build

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
Wait-Ready -Name "usage-service" -Url "http://localhost:8082/actuator/health/readiness"
Wait-Ready -Name "billing-service" -Url "http://localhost:8083/actuator/health/readiness"
Wait-Ready -Name "invoice-batch-service" -Url "http://localhost:8084/actuator/health/readiness"
Wait-Ready -Name "payment-service" -Url "http://localhost:8085/actuator/health/readiness"
Wait-Ready -Name "settlement-service" -Url "http://localhost:8086/actuator/health/readiness"

$env:PERF_AUTH_TOKEN = if ($env:PERF_AUTH_TOKEN) { $env:PERF_AUTH_TOKEN } else { "dev-admin-token" }
$env:TARGET_P95_MS = if ($env:TARGET_P95_MS) { $env:TARGET_P95_MS } else { "300" }
$env:TARGET_ERROR_RATE = if ($env:TARGET_ERROR_RATE) { $env:TARGET_ERROR_RATE } else { "0.01" }
$env:PERF_VUS = if ($env:PERF_VUS) { $env:PERF_VUS } else { "10" }
$env:PERF_DURATION = if ($env:PERF_DURATION) { $env:PERF_DURATION } else { "3m" }
$env:TENANT_BASE_URL = if ($env:TENANT_BASE_URL) { $env:TENANT_BASE_URL } else { "http://tenant-service:8081" }
$env:USAGE_BASE_URL = if ($env:USAGE_BASE_URL) { $env:USAGE_BASE_URL } else { "http://usage-service:8082" }
$env:BILLING_BASE_URL = if ($env:BILLING_BASE_URL) { $env:BILLING_BASE_URL } else { "http://billing-service:8083" }
$env:INVOICE_BASE_URL = if ($env:INVOICE_BASE_URL) { $env:INVOICE_BASE_URL } else { "http://invoice-batch-service:8084" }

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
  -e USAGE_BASE_URL `
  -e BILLING_BASE_URL `
  -e INVOICE_BASE_URL `
  grafana/k6:0.52.0 `
  run `
  --summary-export /work/perf/results/k6-summary.json `
  /work/perf/k6/scenarios/platform-flow.js

docker compose -f docker-compose.local.yml logs --no-color | Out-File -Encoding utf8 "perf/results/docker-compose.log"

Write-Output "Perf test completed. Summary: perf/results/k6-summary.json"
