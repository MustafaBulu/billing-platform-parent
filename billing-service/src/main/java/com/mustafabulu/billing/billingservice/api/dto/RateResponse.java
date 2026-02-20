package com.mustafabulu.billing.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RateResponse(
        String tenantId,
        String customerId,
        String metricCode,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String currency,
        Instant ratedAt
) {
}
