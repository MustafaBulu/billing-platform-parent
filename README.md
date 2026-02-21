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

## Next Iterations
- Add MongoDB + Oracle persistence adapters
- Introduce outbox/inbox and saga state store
- Add Testcontainers integration test suites per service
- Add SOAP client/server contract tests
