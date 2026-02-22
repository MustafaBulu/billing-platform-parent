package com.mustafabulu.billing.invoicebatchservice.persistence;

public enum OrchestrationStatus {
    RECEIVED,
    INVOICE_GENERATED,
    PAYMENT_COMPLETED,
    SETTLEMENT_COMPLETED,
    COMPENSATION_IN_PROGRESS,
    COMPENSATED,
    TIMED_OUT,
    FAILED,
    COMPENSATION_REQUIRED
}
