package com.mustafabulu.billing.invoicebatchservice.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record Invoice(
        @Schema(description = "Invoice identifier", example = "inv-2026-0001")
        String invoiceId,
        @Schema(description = "Tenant identifier", example = "acme-tr")
        String tenantId,
        @Schema(description = "Customer identifier", example = "cust-1001")
        String customerId,
        @Schema(description = "Billing period (yyyy-MM)", example = "2026-02")
        String billingPeriod,
        @Schema(description = "Invoice total amount", example = "60.00")
        BigDecimal totalAmount,
        @Schema(description = "ISO currency code", example = "USD")
        String currency,
        @Schema(description = "Invoice status", example = "PENDING")
        String status,
        @Schema(description = "Creation timestamp in UTC", example = "2026-02-21T16:30:00Z")
        Instant createdAt
) {
}
