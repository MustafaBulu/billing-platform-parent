package com.mustafabulu.billing.billingservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record RateResponse(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        String tenantId,
        @Schema(description = "Customer identifier", example = "cust-1001")
        String customerId,
        @Schema(description = "Metric code", example = "api_call")
        String metricCode,
        @Schema(description = "Rated quantity", example = "1200")
        long quantity,
        @Schema(description = "Unit price", example = "0.05")
        BigDecimal unitPrice,
        @Schema(description = "Calculated total amount", example = "60.00")
        BigDecimal totalAmount,
        @Schema(description = "ISO currency code", example = "USD")
        String currency,
        @Schema(description = "Rating timestamp in UTC", example = "2026-02-21T16:30:00Z")
        Instant ratedAt
) {
}
