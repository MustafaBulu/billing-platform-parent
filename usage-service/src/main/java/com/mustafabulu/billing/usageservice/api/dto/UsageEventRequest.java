package com.mustafabulu.billing.usageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record UsageEventRequest(
        @NotBlank String tenantId,
        @NotBlank String customerId,
        @NotBlank String idempotencyKey,
        @NotBlank String metricCode,
        @PositiveOrZero long quantity,
        Instant occurredAt
) {
}
