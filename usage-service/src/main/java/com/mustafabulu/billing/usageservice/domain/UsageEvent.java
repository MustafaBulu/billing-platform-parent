package com.mustafabulu.billing.usageservice.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record UsageEvent(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        String tenantId,
        @Schema(description = "Customer identifier", example = "cust-1001")
        String customerId,
        @Schema(description = "Idempotency key", example = "usage-evt-0001")
        String idempotencyKey,
        @Schema(description = "Metric code", example = "api_call")
        String metricCode,
        @Schema(description = "Consumed quantity", example = "120")
        long quantity,
        @Schema(description = "Event timestamp in UTC", example = "2026-02-21T16:30:00Z")
        Instant occurredAt
) {
}
