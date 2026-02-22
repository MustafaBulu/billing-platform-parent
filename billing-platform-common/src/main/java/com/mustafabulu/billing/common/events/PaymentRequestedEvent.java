package com.mustafabulu.billing.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequestedEvent(
        String eventId,
        String tenantId,
        String orchestrationId,
        String idempotencyKey,
        String invoiceId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
