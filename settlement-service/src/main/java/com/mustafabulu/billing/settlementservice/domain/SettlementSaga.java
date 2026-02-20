package com.mustafabulu.billing.settlementservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementSaga(
        String sagaId,
        String tenantId,
        String invoiceId,
        String paymentTransactionId,
        BigDecimal amount,
        String currency,
        String status,
        Instant updatedAt
) {
}
