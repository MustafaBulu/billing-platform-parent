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
- `platform.security.auth.mode=none` (`none` | `api-key` | `bearer`)
- `platform.security.api-key.value=${PLATFORM_SECURITY_API_KEY_VALUE:}`
- `platform.security.bearer.tokens=${PLATFORM_SECURITY_BEARER_TOKENS:}` (comma-separated allowlist)
- `platform.security.tenant-guard.enabled=false`
- `platform.rate-limit.enabled=true`
- `platform.rate-limit.max-requests=240`
- `platform.rate-limit.window-seconds=60`

If `platform.security.auth.mode=api-key`, send:
- Header: `X-API-Key: <your-key>`

If `platform.security.auth.mode=bearer`, send:
- Header: `Authorization: Bearer <token>`

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
export PLATFORM_SECURITY_BEARER_TOKENS='dev-token-1,dev-token-2'
export GF_SECURITY_ADMIN_USER='admin'
export GF_SECURITY_ADMIN_PASSWORD='strong-local-password'
```

### Orchestration Reliability
- Invoice service now includes outbox publisher job:
  - `platform.outbox.publisher.enabled=true`
  - `platform.outbox.publisher.batch-size=50`
  - `platform.outbox.publisher.fixed-delay-ms=5000`
- Outbox events are marked `SENT`/`FAILED` with retry attempt tracking.

## Next Iterations
- Add MongoDB + Oracle persistence adapters
- Introduce outbox/inbox and saga state store
- Add Testcontainers integration test suites per service
- Add SOAP client/server contract tests
