package com.mustafabulu.billing.invoicebatchservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record GenerateInvoiceRequest(
        @NotBlank String tenantId,
        @NotBlank String customerId,
        @NotBlank String billingPeriod,
        @NotBlank String currency,
        @NotEmpty List<@NotNull BigDecimal> lineAmounts,
        String idempotencyKey
) {
}
