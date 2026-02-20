package com.mustafabulu.billing.invoicebatchservice.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SettlementResponse(
        String sagaId,
        String tenantId,
        String invoiceId,
        String paymentTransactionId,
        BigDecimal amount,
        String currency,
        String status,
        List<String> transitions,
        Instant updatedAt
) {
}
