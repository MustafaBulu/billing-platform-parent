package com.mustafabulu.billing.invoicebatchservice.application.dto;

import java.math.BigDecimal;

public record PaymentProcessRequest(
        String tenantId,
        String invoiceId,
        String idempotencyKey,
        BigDecimal amount,
        String currency
) {
}
