package com.mustafabulu.billing.billingservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record RateRequest(
        @NotBlank String tenantId,
        @NotBlank String customerId,
        @NotBlank String metricCode,
        @PositiveOrZero long quantity,
        @NotNull BigDecimal unitPrice,
        @NotBlank String currency
) {
}
