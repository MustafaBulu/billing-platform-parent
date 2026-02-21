package com.mustafabulu.billing.invoicebatchservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record GenerateInvoiceRequest(
        @Schema(description = "Tenant identifier", example = "acme-tr")
        @NotBlank String tenantId,
        @Schema(description = "Customer identifier", example = "cust-1001")
        @NotBlank String customerId,
        @Schema(description = "Billing period (yyyy-MM)", example = "2026-02")
        @NotBlank String billingPeriod,
        @Schema(description = "ISO currency code", example = "USD")
        @NotBlank String currency,
        @Schema(description = "Line item amounts that will be summed", example = "[15.0,20.0,25.0]")
        @NotEmpty List<@NotNull BigDecimal> lineAmounts,
        @Schema(description = "Optional idempotency key for deduplication", example = "inv-evt-0001")
        String idempotencyKey
) {
}
