# Billing Platform Parent

<p align="left">
  <a href="https://www.java.com/" title="Java"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="40" height="40" alt="Java"/></a>
  <a href="https://spring.io/projects/spring-boot" title="Spring Boot"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="40" height="40" alt="Spring Boot"/></a>
  <a href="https://maven.apache.org/" title="Maven"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/maven/maven-original.svg" width="40" height="40" alt="Maven"/></a>
  <a href="https://www.mongodb.com/" title="MongoDB"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/mongodb/mongodb-original.svg" width="40" height="40" alt="MongoDB"/></a>
  <a href="https://prometheus.io/" title="Prometheus"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/prometheus/prometheus-original.svg" width="40" height="40" alt="Prometheus"/></a>
  <a href="https://grafana.com/" title="Grafana"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/grafana/grafana-original.svg" width="40" height="40" alt="Grafana"/></a>
  <a href="https://www.docker.com/" title="Docker"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/docker/docker-original.svg" width="40" height="40" alt="Docker"/></a>
  <a href="https://kubernetes.io/" title="Kubernetes"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/kubernetes/kubernetes-plain.svg" width="40" height="40" alt="Kubernetes"/></a>
  <a href="https://github.com/features/actions" title="GitHub Actions"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/githubactions/githubactions-original.svg" width="40" height="40" alt="GitHub Actions"/></a>
</p>

This is a Spring Boot microservices billing playground focused on multi-tenant usage rating, invoice orchestration, payment, settlement, and production-style observability.

- Platform scope: tenant, usage, billing, invoice, payment, settlement
- Observability: Prometheus + Grafana + Loki
- Delivery: GitHub Actions CI/CD + SonarCloud + Gitleaks

## Architecture

### Microservices

- `billing-platform-common`: shared tenant context, idempotency, security/web filters, common exceptions
- `tenant-service` (`8081`): tenant management APIs
- `usage-service` (`8082`): usage ingestion and aggregate totals
- `billing-service` (`8083`): usage rating and charge calculation
- `invoice-batch-service` (`8084`): invoice generation and generate-and-settle orchestration
- `payment-service` (`8085`): payment processing endpoint/facade
- `settlement-service` (`8086`): settlement saga start/query endpoints

### Infrastructure (Local / Docker Compose)

- MongoDB (`27017`)
- Prometheus (`9091`)
- Grafana (`3300`)
- Loki + Promtail

## Orchestration Flow

`POST /api/v1/invoices/generate-and-settle` flow:

1. Invoice is generated in `invoice-batch-service`.
2. Payment call is sent to `payment-service`.
3. Settlement call is sent to `settlement-service`.
4. Orchestration/outbox records are persisted for reliability and replay.

## Prerequisites

- Docker Desktop
- Docker Compose
- Java 21 (for local non-Docker runs)

## Setup

1. Build the project:

```bash
./mvnw -B -ntp clean verify
```

PowerShell:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

2. Start full stack:

```bash
docker compose -f docker-compose.local.yml up -d --build
```

3. Check health:

```bash
curl http://localhost:8081/actuator/health/readiness
curl http://localhost:8082/actuator/health/readiness
curl http://localhost:8083/actuator/health/readiness
curl http://localhost:8084/actuator/health/readiness
curl http://localhost:8085/actuator/health/readiness
curl http://localhost:8086/actuator/health/readiness
```

## Service URLs

- Tenant Service: `http://localhost:8081`
- Usage Service: `http://localhost:8082`
- Billing Service: `http://localhost:8083`
- Invoice Service: `http://localhost:8084`
- Payment Service: `http://localhost:8085`
- Settlement Service: `http://localhost:8086`
- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3300`

## API Documentation (Swagger)

- Tenant Swagger: `http://localhost:8081/swagger-ui/index.html`
- Usage Swagger: `http://localhost:8082/swagger-ui/index.html`
- Billing Swagger: `http://localhost:8083/swagger-ui/index.html`
- Invoice Swagger: `http://localhost:8084/swagger-ui/index.html`
- Payment Swagger: `http://localhost:8085/swagger-ui/index.html`
- Settlement Swagger: `http://localhost:8086/swagger-ui/index.html`

OpenAPI JSON format:

- `http://localhost:<port>/v3/api-docs`

## Security and Headers

Main security properties:

- `platform.security.auth.mode=none|api-key|bearer`
- `platform.security.authorization.enabled=true|false`
- `platform.security.tenant-guard.enabled=true|false`

Common headers:

- `Authorization: Bearer <token>`
- `X-API-Key: <api-key>`
- `X-Tenant-Id: <tenant-id>`
- `X-Request-Id: <request-id>`
- `Idempotency-Key: <idempotency-key>`

## Metrics and Logs

Metrics endpoints:

- `http://localhost:<port>/actuator/prometheus`

Health endpoints:

- `http://localhost:<port>/actuator/health/liveness`
- `http://localhost:<port>/actuator/health/readiness`

## CI/CD

Workflows:

- CI: `.github/workflows/ci.yml`
- CD: `.github/workflows/cd.yml`
- Performance: `.github/workflows/perf.yml`

CI pipeline includes:

- Gitleaks secret scan
- Maven `clean verify`
- Sonar analysis (`mvn sonar:sonar`)
- Surefire/Failsafe artifact upload

### SonarCloud Configuration

Set these in GitHub `Secrets` or `Variables`:

- `SONAR_HOST_URL` (`https://sonarcloud.io`)
- `SONAR_TOKEN`
- `SONAR_PROJECT_KEY`
- `SONAR_ORGANIZATION`

Important:

- SonarCloud `Automatic Analysis` must be disabled for this project.

## Performance Testing

- k6 scenario: `perf/k6/scenarios/platform-flow.js`
- local run script: `perf/run-local.ps1`
- CI perf workflow: `.github/workflows/perf.yml`

## Kubernetes

Base manifests:

- `ops/k8s/base`

Apply:

```bash
kubectl apply -k ops/k8s/base
kubectl -n billing-platform get pods,svc,ingress,hpa
```

## Testing

Run all tests:

```bash
./mvnw -B -ntp test
```

Run module tests:

```bash
./mvnw -B -ntp -pl usage-service test
./mvnw -B -ntp -pl invoice-batch-service test
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
├─ perf/
└─ .github/workflows/
```
