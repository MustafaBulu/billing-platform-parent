package com.mustafabulu.billing.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementResultEvent(
        String eventId,
        String tenantId,
        String orchestrationId,
        String idempotencyKey,
        String sagaId,
        String invoiceId,
        String paymentTransactionId,
        BigDecimal amount,
        String currency,
        String status,
        Instant updatedAt
) {
}
