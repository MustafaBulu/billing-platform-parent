$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path "perf/results" | Out-Null

docker compose -f docker-compose.local.yml up -d --build

$env:PERF_AUTH_TOKEN = if ($env:PERF_AUTH_TOKEN) { $env:PERF_AUTH_TOKEN } else { "dev-admin-token" }
$env:TARGET_P95_MS = if ($env:TARGET_P95_MS) { $env:TARGET_P95_MS } else { "300" }
$env:TARGET_ERROR_RATE = if ($env:TARGET_ERROR_RATE) { $env:TARGET_ERROR_RATE } else { "0.01" }
$env:PERF_VUS = if ($env:PERF_VUS) { $env:PERF_VUS } else { "10" }
$env:PERF_DURATION = if ($env:PERF_DURATION) { $env:PERF_DURATION } else { "3m" }

docker run --rm `
  --network host `
  -v "${PWD}:/work" `
  -w /work `
  -e PERF_AUTH_TOKEN `
  -e TARGET_P95_MS `
  -e TARGET_ERROR_RATE `
  -e PERF_VUS `
  -e PERF_DURATION `
  grafana/k6:0.52.0 `
  run `
  --summary-export /work/perf/results/k6-summary.json `
  /work/perf/k6/scenarios/platform-flow.js

docker compose -f docker-compose.local.yml logs --no-color | Out-File -Encoding utf8 "perf/results/docker-compose.log"

Write-Output "Perf test completed. Summary: perf/results/k6-summary.json"
