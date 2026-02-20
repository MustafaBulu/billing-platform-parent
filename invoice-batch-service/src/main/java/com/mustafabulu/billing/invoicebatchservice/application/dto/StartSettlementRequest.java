package com.mustafabulu.billing.invoicebatchservice.application.dto;

import java.math.BigDecimal;

public record StartSettlementRequest(
        String tenantId,
        String invoiceId,
        String paymentTransactionId,
        BigDecimal amount,
        String currency,
        String paymentStatus
) {
}
