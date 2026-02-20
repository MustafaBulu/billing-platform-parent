package com.mustafabulu.billing.invoicebatchservice.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentProcessResponse(
        String transactionId,
        String invoiceId,
        BigDecimal amount,
        String currency,
        String status,
        String providerReference,
        Instant processedAt
) {
}
