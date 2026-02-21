package com.mustafabulu.billing.invoicebatchservice.application.dto;

import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;
import io.swagger.v3.oas.annotations.media.Schema;

public record InvoiceOrchestrationResult(
        @Schema(description = "Generated invoice details")
        Invoice invoice,
        @Schema(description = "Payment processing result")
        PaymentProcessResponse payment,
        @Schema(description = "Settlement result")
        SettlementResponse settlement
) {
}
