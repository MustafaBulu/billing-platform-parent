package com.mustafabulu.billing.common.events;

import java.time.Instant;

public record PaymentCompensationRequestedEvent(
        String eventId,
        String tenantId,
        String orchestrationId,
        String idempotencyKey,
        String transactionId,
        String reason,
        Instant occurredAt
) {
}
