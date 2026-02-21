# Multi-Tenant Usage-Based Billing & Settlement SaaS Platform

Cloud-native multi-module Maven project for a multi-tenant billing infrastructure.

## Modules
- `billing-platform-common`: shared multi-tenant context, DTOs, and exceptions
- `tenant-service`: tenant and plan management
- `usage-service`: usage ingestion and idempotent tracking
- `billing-service`: charge calculation domain entrypoint
- `invoice-batch-service`: invoice generation batch jobs
- `payment-service`: SOAP-facing payment processing facade
- `settlement-service`: saga-oriented settlement orchestration facade

## Tech Stack
- Java 21
- Spring Boot 3.3.x
- Maven multi-module
- Spring Batch (invoice service)
- Spring Web + Validation

## Build
```bash
./mvnw clean verify
```

## Run a Service
```bash
./mvnw -pl tenant-service spring-boot:run
```

## IntelliJ Quick Start
1. Open the root project as a Maven project and set Project SDK to Java 21.
2. Start local MongoDB once:
```bash
docker compose -f docker-compose.local.yml up -d
```
3. Run any service from IntelliJ:
   - `Run > Edit Configurations... > + > Spring Boot`
   - Main class examples:
     - `com.mustafabulu.billing.tenantservice.TenantServiceApplication`
     - `com.mustafabulu.billing.usageservice.UsageServiceApplication`
     - `com.mustafabulu.billing.billingservice.BillingServiceApplication`
     - `com.mustafabulu.billing.invoicebatchservice.InvoiceBatchServiceApplication`
     - `com.mustafabulu.billing.paymentservice.PaymentServiceApplication`
     - `com.mustafabulu.billing.settlementservice.SettlementServiceApplication`
4. If ports are busy, change `server.port` in the corresponding module `application.properties`.

### Local Service URLs
- Tenant: `http://localhost:8081`
- Usage: `http://localhost:8082`
- Billing: `http://localhost:8083`
- Invoice: `http://localhost:8084`
- Payment: `http://localhost:8085`
- Settlement: `http://localhost:8086`

### OpenAPI / Swagger
- OpenAPI JSON: `http://localhost:<port>/v3/api-docs`
- Swagger UI: `http://localhost:<port>/swagger-ui/index.html`

### Security & Observability Toggles
Common defaults are added to each service `application.properties`:
- `platform.security.auth.mode=bearer` (`none` | `api-key` | `bearer`)
- `platform.security.authorization.enabled=true`
- `platform.security.api-key.value=${PLATFORM_SECURITY_API_KEY_VALUE:}`
- `platform.security.bearer.tokens=${PLATFORM_SECURITY_BEARER_TOKENS:dev-admin-token}` (comma-separated allowlist)
- `platform.security.bearer.token-scopes=${PLATFORM_SECURITY_BEARER_TOKEN_SCOPES:...}`
- `platform.security.bearer.token-tenants=${PLATFORM_SECURITY_BEARER_TOKEN_TENANTS:...}`
- `platform.security.tenant-guard.enabled=true`
- `platform.rate-limit.enabled=true`
- `platform.rate-limit.max-requests=240`
- `platform.rate-limit.window-seconds=60`

If `platform.security.auth.mode=api-key`, send:
- Header: `X-API-Key: <your-key>`

If `platform.security.auth.mode=bearer`, send:
- Header: `Authorization: Bearer <token>`
- Scope mapping format:
  - `PLATFORM_SECURITY_BEARER_TOKEN_SCOPES='tokenA=usage:read|usage:write;tokenB=invoice:read'`
- Tenant mapping format:
  - `PLATFORM_SECURITY_BEARER_TOKEN_TENANTS='tokenA=*;tokenB=tenant-a|tenant-b'`

If `platform.security.tenant-guard.enabled=true`, send:
- Header: `X-Tenant-Id: <tenant-id>`

Request correlation:
- Request header (optional): `X-Request-Id`
- Response header (always): `X-Request-Id`

### Observability
- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3300`
- Service metrics: `http://localhost:<port>/actuator/prometheus`
- Liveness/Readiness: `http://localhost:<port>/actuator/health/liveness`, `http://localhost:<port>/actuator/health/readiness`

Example local env values:
```bash
export PLATFORM_SECURITY_API_KEY_VALUE='dev-api-key'
export PLATFORM_SECURITY_BEARER_TOKENS='dev-admin-token'
export PLATFORM_SECURITY_BEARER_TOKEN_SCOPES='dev-admin-token=tenant:write|usage:write|usage:read|billing:write|invoice:write|invoice:read|invoice:settle|payment:write|settlement:write|settlement:read'
export PLATFORM_SECURITY_BEARER_TOKEN_TENANTS='dev-admin-token=*'
export GF_SECURITY_ADMIN_USER='admin'
export GF_SECURITY_ADMIN_PASSWORD='strong-local-password'
```

### Orchestration Reliability
- Invoice service now includes outbox publisher job:
  - `platform.outbox.publisher.enabled=true`
  - `platform.outbox.publisher.batch-size=50`
  - `platform.outbox.publisher.fixed-delay-ms=5000`
- Outbox events are marked `SENT`/`FAILED` with retry attempt tracking.

### Performance Testing (k6)
- End-to-end load scenario script: `perf/k6/scenarios/platform-flow.js`
- Local runner: `perf/run-local.ps1`
- CI workflow: `.github/workflows/perf.yml` (manual + nightly)

Baseline targets used in k6 thresholds:
- `p95` latency: `< 300ms`
- request error rate: `< 1%`
- default load profile: `10 VUs` for `3m`

Run locally (PowerShell):
```powershell
./perf/run-local.ps1
```

Override defaults when needed:
```powershell
$env:PERF_AUTH_TOKEN="dev-admin-token"
$env:TARGET_P95_MS="300"
$env:TARGET_ERROR_RATE="0.01"
$env:PERF_VUS="20"
$env:PERF_DURATION="5m"
./perf/run-local.ps1
```

Generated artifacts:
- `perf/results/k6-summary.json`
- `perf/results/docker-compose.log`

### Kubernetes (Base Manifests)
Kubernetes base manifests are under:
- `ops/k8s/base`

Includes:
- namespace, configmap, secret
- Mongo deployment + PVC
- all 6 services (`Deployment + Service`)
- Ingress (`nginx` class) with host-based routing
- HPA objects for each service

Apply:
```bash
kubectl apply -k ops/k8s/base
```

Check:
```bash
kubectl -n billing-platform get pods,svc,ingress,hpa
```

Ingress hosts (for local nginx ingress + localtest.me):
- `tenant.localtest.me`
- `usage.localtest.me`
- `billing.localtest.me`
- `invoice.localtest.me`
- `payment.localtest.me`
- `settlement.localtest.me`

Example:
```bash
curl -H "Authorization: Bearer dev-admin-token" \
     -H "Content-Type: application/json" \
     -d '{"displayName":"Acme Turkey"}' \
     http://tenant.localtest.me/api/v1/tenants
```

Notes:
- Image names in manifests use local tags generated by compose build, e.g. `billing-platform-parent-tenant-service:latest`.
- For managed clusters, push images to a registry and update `image:` fields.
- `ops/k8s/base/secret.yaml` is dev-only; replace with cluster secret manager for production.

### CI/CD (GitHub Actions)
- CI workflow: `.github/workflows/ci.yml`
  - Trigger: push + pull request
  - Runs: `./mvnw -B -ntp clean verify`
  - Uploads surefire/failsafe test reports as artifacts
- CD workflow: `.github/workflows/cd.yml`
  - Trigger: CI success on `main` or manual dispatch on `main`
  - Builds and pushes all service images to GHCR:
    - `ghcr.io/<owner>/billing-platform-<service>:sha-<commit>`
    - `ghcr.io/<owner>/billing-platform-<service>:latest`
  - Optional Kubernetes deploy step (enabled when `KUBE_CONFIG` secret is set)

Required secrets for deployment:
- `KUBE_CONFIG` (base64 or raw kubeconfig content)

## Next Iterations
- Add MongoDB + Oracle persistence adapters
- Introduce outbox/inbox and saga state store
- Add Testcontainers integration test suites per service
- Add SOAP client/server contract tests
