package com.mustafabulu.billing.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResultEvent(
        String eventId,
        String tenantId,
        String orchestrationId,
        String idempotencyKey,
        String invoiceId,
        String transactionId,
        BigDecimal amount,
        String currency,
        String status,
        String providerReference,
        Instant processedAt
) {
}
