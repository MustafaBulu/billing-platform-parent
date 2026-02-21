package com.mustafabulu.billing.invoicebatchservice.persistence;

public enum OrchestrationStatus {
    RECEIVED,
    INVOICE_GENERATED,
    PAYMENT_COMPLETED,
    SETTLEMENT_COMPLETED,
    FAILED,
    COMPENSATION_REQUIRED
}
