package com.mustafabulu.billing.usageservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record UsageEventRequest(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        @NotBlank String tenantId,
        @Schema(description = "Customer identifier", example = "cust-1001")
        @NotBlank String customerId,
        @Schema(description = "Idempotency key for deduplication", example = "usage-evt-0001")
        @NotBlank String idempotencyKey,
        @Schema(description = "Metric code", example = "api_call")
        @NotBlank String metricCode,
        @Schema(description = "Metered quantity", example = "120")
        @PositiveOrZero long quantity,
        @Schema(description = "Event timestamp in UTC", example = "2026-02-21T16:30:00Z")
        Instant occurredAt
) {
}
