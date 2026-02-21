package com.mustafabulu.billing.settlementservice.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SettlementSaga(
        @Schema(description = "Settlement saga identifier", example = "saga-8d11")
        String sagaId,
        @Schema(description = "Tenant identifier", example = "acme-tr")
        String tenantId,
        @Schema(description = "Invoice identifier", example = "inv-2026-0001")
        String invoiceId,
        @Schema(description = "Payment transaction identifier", example = "txn-19af2")
        String paymentTransactionId,
        @Schema(description = "Settlement amount", example = "60.00")
        BigDecimal amount,
        @Schema(description = "ISO currency code", example = "USD")
        String currency,
        @Schema(description = "Current saga status", example = "SETTLED")
        SettlementStatus status,
        @Schema(description = "Ordered list of visited statuses", example = "[\"STARTED\",\"PAYMENT_CONFIRMED\",\"SETTLED\"]")
        List<SettlementStatus> transitions,
        @Schema(description = "Last update timestamp in UTC", example = "2026-02-21T16:30:02Z")
        Instant updatedAt
) {
}
