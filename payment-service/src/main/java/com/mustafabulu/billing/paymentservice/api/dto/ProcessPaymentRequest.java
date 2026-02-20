package com.mustafabulu.billing.paymentservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProcessPaymentRequest(
        @NotBlank String tenantId,
        @NotBlank String invoiceId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency
) {
}
