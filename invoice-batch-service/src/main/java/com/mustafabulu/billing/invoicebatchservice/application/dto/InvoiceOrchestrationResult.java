package com.mustafabulu.billing.invoicebatchservice.application.dto;

import com.mustafabulu.billing.invoicebatchservice.domain.Invoice;

public record InvoiceOrchestrationResult(
        Invoice invoice,
        PaymentProcessResponse payment,
        SettlementResponse settlement
) {
}
