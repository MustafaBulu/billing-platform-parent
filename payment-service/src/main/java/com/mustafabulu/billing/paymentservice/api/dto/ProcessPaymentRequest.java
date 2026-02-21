package com.mustafabulu.billing.paymentservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProcessPaymentRequest(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        @NotBlank String tenantId,
        @Schema(description = "Invoice identifier", example = "inv-2026-0001")
        @NotBlank String invoiceId,
        @Schema(description = "Idempotency key for payment request", example = "pay-evt-0001")
        @NotBlank String idempotencyKey,
        @Schema(description = "Requested payment amount", example = "60.00")
        @NotNull @Positive BigDecimal amount,
        @Schema(description = "ISO currency code", example = "USD")
        @NotBlank String currency
) {
}
