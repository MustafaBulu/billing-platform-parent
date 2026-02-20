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

## Next Iterations
- Add MongoDB + Oracle persistence adapters
- Introduce outbox/inbox and saga state store
- Add Testcontainers integration test suites per service
- Add SOAP client/server contract tests
