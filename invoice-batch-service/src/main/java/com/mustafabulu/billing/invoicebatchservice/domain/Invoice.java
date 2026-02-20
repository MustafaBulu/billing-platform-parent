package com.mustafabulu.billing.invoicebatchservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Invoice(
        String invoiceId,
        String tenantId,
        String customerId,
        String billingPeriod,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant createdAt
) {
}
