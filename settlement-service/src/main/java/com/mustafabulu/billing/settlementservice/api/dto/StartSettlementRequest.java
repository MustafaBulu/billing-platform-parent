package com.mustafabulu.billing.settlementservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record StartSettlementRequest(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        @NotBlank String tenantId,
        @Schema(description = "Invoice identifier", example = "inv-2026-0001")
        @NotBlank String invoiceId,
        @Schema(description = "Payment transaction identifier", example = "txn-19af2")
        @NotBlank String paymentTransactionId,
        @Schema(description = "Idempotency key for settlement request", example = "set-evt-0001")
        @NotBlank String idempotencyKey,
        @Schema(description = "Settlement amount", example = "60.00")
        @NotNull @Positive BigDecimal amount,
        @Schema(description = "ISO currency code", example = "USD")
        @NotBlank String currency,
        @Schema(description = "Payment state provided by payment service", example = "AUTHORIZED")
        @NotBlank String paymentStatus
) {
}
