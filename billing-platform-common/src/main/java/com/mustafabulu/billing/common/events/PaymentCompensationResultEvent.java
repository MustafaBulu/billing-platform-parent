package com.mustafabulu.billing.common.events;

import java.time.Instant;

public record PaymentCompensationResultEvent(
        String eventId,
        String tenantId,
        String orchestrationId,
        String idempotencyKey,
        String transactionId,
        String status,
        String reason,
        Instant processedAt
) {
}
