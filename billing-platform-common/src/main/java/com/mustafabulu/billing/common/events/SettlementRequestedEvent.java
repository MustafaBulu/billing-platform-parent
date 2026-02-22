package com.mustafabulu.billing.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementRequestedEvent(
        String eventId,
        String tenantId,
        String orchestrationId,
        String idempotencyKey,
        String invoiceId,
        String paymentTransactionId,
        BigDecimal amount,
        String currency,
        String paymentStatus,
        Instant occurredAt
) {
}
