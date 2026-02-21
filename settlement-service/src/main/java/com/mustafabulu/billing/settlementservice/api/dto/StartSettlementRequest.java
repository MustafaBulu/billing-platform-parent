package com.mustafabulu.billing.settlementservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record StartSettlementRequest(
        @NotBlank String tenantId,
        @NotBlank String invoiceId,
        @NotBlank String paymentTransactionId,
        @NotBlank String idempotencyKey,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String paymentStatus
) {
}
