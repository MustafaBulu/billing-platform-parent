# Billing Platform Parent

Multi-tenant, usage-based billing platform built with Java 21 and Spring Boot.  
This repository contains six cooperating microservices plus shared platform components.

## What This Project Provides

- Multi-tenant context propagation (`X-Tenant-Id`)
- Usage ingestion and aggregation
- Rating and billing calculations
- Invoice generation and orchestration
- Payment processing facade
- Settlement saga facade
- Shared security, idempotency, error handling, and request correlation
- CI/CD with GitHub Actions + SonarCloud analysis + Gitleaks

## Modules

| Module | Purpose | Default Port |
|---|---|---|
| `billing-platform-common` | Shared filters, exceptions, tenant/idempotency/web utilities | - |
| `tenant-service` | Tenant lifecycle APIs | `8081` |
| `usage-service` | Usage event ingestion and totals | `8082` |
| `billing-service` | Rating / billable amount calculation | `8083` |
| `invoice-batch-service` | Invoice generation + orchestration flow | `8084` |
| `payment-service` | Payment processing endpoint/facade | `8085` |
| `settlement-service` | Settlement orchestration/saga endpoint | `8086` |

## Tech Stack

- Java 21
- Spring Boot 3.3.x
- Maven multi-module
- MongoDB
- Docker / Docker Compose
- GitHub Actions (CI/CD)
- SonarCloud
- Grafana + Prometheus + Loki
- k6 (performance testing)

## Quick Start (Local)

### 1. Prerequisites

- JDK 21
- Docker Desktop (or Docker Engine + Compose)

### 2. Build

```bash
./mvnw -B -ntp clean verify
```

PowerShell:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

### 3. Start full local platform (recommended)

```bash
docker compose -f docker-compose.local.yml up -d --build
```

### 4. Health checks

- `http://localhost:8081/actuator/health/readiness`
- `http://localhost:8082/actuator/health/readiness`
- `http://localhost:8083/actuator/health/readiness`
- `http://localhost:8084/actuator/health/readiness`
- `http://localhost:8085/actuator/health/readiness`
- `http://localhost:8086/actuator/health/readiness`

## Run a Single Service

```bash
./mvnw -pl tenant-service spring-boot:run
```

PowerShell:

```powershell
.\mvnw.cmd -pl tenant-service spring-boot:run
```

## API Docs

- OpenAPI JSON: `http://localhost:<port>/v3/api-docs`
- Swagger UI: `http://localhost:<port>/swagger-ui/index.html`

## Security and Request Headers

Common auth properties (per service):

- `platform.security.auth.mode` = `none | api-key | bearer`
- `platform.security.authorization.enabled=true|false`
- `platform.security.tenant-guard.enabled=true|false`

Headers:

- `Authorization: Bearer <token>` (bearer mode)
- `X-API-Key: <key>` (api-key mode)
- `X-Tenant-Id: <tenant>` (tenant guard enabled endpoints)
- `X-Request-Id: <id>` (optional; echoed back)
- `Idempotency-Key: <key>` where applicable

## Observability

- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3300`
- Metrics: `http://localhost:<port>/actuator/prometheus`
- Liveness: `http://localhost:<port>/actuator/health/liveness`
- Readiness: `http://localhost:<port>/actuator/health/readiness`

## Performance Testing

k6 scenario:

- `perf/k6/scenarios/platform-flow.js`

Local runner:

```powershell
./perf/run-local.ps1
```

## Kubernetes Base Manifests

Path:

- `ops/k8s/base`

Apply:

```bash
kubectl apply -k ops/k8s/base
```

Check:

```bash
kubectl -n billing-platform get pods,svc,ingress,hpa
```

## CI/CD

Workflows:

- `CI`: `.github/workflows/ci.yml`
- `CD`: `.github/workflows/cd.yml`
- `Performance`: `.github/workflows/perf.yml`

### CI Includes

- Gitleaks scan
- Maven `clean verify`
- Sonar analysis via Maven scanner
- Test report artifact upload

### SonarCloud Requirements (for this repo)

Set these in GitHub `Secrets` or `Variables`:

- `SONAR_HOST_URL` (SonarCloud: `https://sonarcloud.io`)
- `SONAR_TOKEN`
- `SONAR_PROJECT_KEY`
- `SONAR_ORGANIZATION` (organization key, usually lowercase)

Important:

- SonarCloud project **Automatic Analysis must be OFF** when CI runs `sonar:sonar`.

## Useful Commands

```bash
# compile fast without tests
./mvnw -B -ntp -DskipTests compile

# run all tests
./mvnw -B -ntp test

# run a single module tests
./mvnw -B -ntp -pl usage-service test
```

## Repository Layout

```text
.
├─ billing-platform-common/
├─ tenant-service/
├─ usage-service/
├─ billing-service/
├─ invoice-batch-service/
├─ payment-service/
├─ settlement-service/
├─ ops/
│  ├─ k8s/
│  ├─ grafana/
│  └─ prometheus/
├─ perf/
└─ .github/workflows/
```

## Notes

- Default branch CI concurrency cancels older in-progress runs on the same branch.
- Existing Sonar issue history/activity is immutable; new analyses reflect current configuration and identity.
