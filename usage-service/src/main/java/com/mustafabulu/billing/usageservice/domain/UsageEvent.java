package com.mustafabulu.billing.usageservice.domain;

import java.time.Instant;

public record UsageEvent(
        String tenantId,
        String customerId,
        String idempotencyKey,
        String metricCode,
        long quantity,
        Instant occurredAt
) {
}
