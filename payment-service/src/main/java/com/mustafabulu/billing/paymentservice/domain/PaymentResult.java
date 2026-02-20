package com.mustafabulu.billing.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResult(
        String transactionId,
        String invoiceId,
        BigDecimal amount,
        String currency,
        String status,
        String providerReference,
        Instant processedAt
) {
}
