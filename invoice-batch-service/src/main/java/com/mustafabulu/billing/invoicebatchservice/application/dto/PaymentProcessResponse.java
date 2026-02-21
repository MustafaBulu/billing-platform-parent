package com.mustafabulu.billing.invoicebatchservice.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentProcessResponse(
        @Schema(description = "Payment transaction identifier", example = "txn-19af2")
        String transactionId,
        @Schema(description = "Invoice identifier", example = "inv-2026-0001")
        String invoiceId,
        @Schema(description = "Processed amount", example = "60.00")
        BigDecimal amount,
        @Schema(description = "ISO currency code", example = "USD")
        String currency,
        @Schema(description = "Payment status", example = "AUTHORIZED")
        String status,
        @Schema(description = "Reference from payment provider", example = "sim-gateway-8891")
        String providerReference,
        @Schema(description = "Processing timestamp in UTC", example = "2026-02-21T16:30:01Z")
        Instant processedAt
) {
}
