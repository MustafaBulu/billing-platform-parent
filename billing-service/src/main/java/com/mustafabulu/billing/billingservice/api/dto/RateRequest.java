package com.mustafabulu.billing.billingservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record RateRequest(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        @NotBlank String tenantId,
        @Schema(description = "Customer identifier", example = "cust-1001")
        @NotBlank String customerId,
        @Schema(description = "Metric code", example = "api_call")
        @NotBlank String metricCode,
        @Schema(description = "Measured quantity", example = "1200")
        @PositiveOrZero long quantity,
        @Schema(description = "Unit price for one quantity", example = "0.05")
        @NotNull BigDecimal unitPrice,
        @Schema(description = "ISO currency code", example = "USD")
        @NotBlank String currency
) {
}
