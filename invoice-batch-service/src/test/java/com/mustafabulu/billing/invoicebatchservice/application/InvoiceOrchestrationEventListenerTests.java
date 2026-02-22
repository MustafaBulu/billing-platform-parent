package com.mustafabulu.billing.invoicebatchservice.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.common.events.PaymentResultEvent;
import com.mustafabulu.billing.common.events.SettlementResultEvent;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvoiceOrchestrationEventListenerTests {

    private static final String OPERATION_CODE = "INVOICE_GENERATE_AND_SETTLE";

    private final OrchestrationRecordRepository orchestrationRecordRepository = Mockito.mock(OrchestrationRecordRepository.class);
    private final InvoiceOrchestrationService invoiceOrchestrationService = Mockito.mock(InvoiceOrchestrationService.class);
    private final InvoiceOrchestrationEventListener listener = new InvoiceOrchestrationEventListener(
            orchestrationRecordRepository, invoiceOrchestrationService);

    @Test
    void shouldWriteSettlementRequestedOutboxWhenPaymentSucceeded() {
        PaymentResultEvent event = new PaymentResultEvent(
                "evt-1",
                "tenant-1",
                "ORCH-1",
                "idem-1",
                "INV-1",
                "TX-1",
                new BigDecimal("10.00"),
                "USD",
                "SUCCESS",
                "APPROVED-ref",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        OrchestrationRecordDocument orchestration = orchestration("tenant-1", "idem-1");
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey("tenant-1", OPERATION_CODE, "idem-1"))
                .thenReturn(Optional.of(orchestration));
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onPaymentResult(event);

        verify(orchestrationRecordRepository).save(any(OrchestrationRecordDocument.class));
        verify(invoiceOrchestrationService).writeOutboxEvent(any(OrchestrationRecordDocument.class), Mockito.eq("SETTLEMENT_REQUESTED"), any());
    }

    @Test
    void shouldMarkFailedWhenPaymentFailed() {
        PaymentResultEvent event = new PaymentResultEvent(
                "evt-2",
                "tenant-1",
                "ORCH-1",
                "idem-2",
                "INV-1",
                null,
                new BigDecimal("10.00"),
                "USD",
                "FAILED",
                "DECLINED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        OrchestrationRecordDocument orchestration = orchestration("tenant-1", "idem-2");
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey("tenant-1", OPERATION_CODE, "idem-2"))
                .thenReturn(Optional.of(orchestration));

        listener.onPaymentResult(event);

        verify(invoiceOrchestrationService).markFailed(orchestration, "PAYMENT_FAILED");
    }

    @Test
    void shouldCompleteInboxWhenSettlementSettled() {
        SettlementResultEvent event = new SettlementResultEvent(
                "evt-3",
                "tenant-1",
                "ORCH-1",
                "idem-3",
                "SAGA-1",
                "INV-1",
                "TX-1",
                new BigDecimal("10.00"),
                "USD",
                "SETTLED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        OrchestrationRecordDocument orchestration = orchestration("tenant-1", "idem-3");
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey("tenant-1", OPERATION_CODE, "idem-3"))
                .thenReturn(Optional.of(orchestration));
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onSettlementResult(event);

        verify(orchestrationRecordRepository).save(any(OrchestrationRecordDocument.class));
        verify(invoiceOrchestrationService).markInboxCompleted("tenant-1", "idem-3", "ORCH-1");
    }

    @Test
    void shouldSetCompensationRequiredWhenSettlementFailed() {
        SettlementResultEvent event = new SettlementResultEvent(
                "evt-4",
                "tenant-1",
                "ORCH-1",
                "idem-4",
                "SAGA-2",
                "INV-2",
                "TX-2",
                new BigDecimal("20.00"),
                "USD",
                "FAILED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        OrchestrationRecordDocument orchestration = orchestration("tenant-1", "idem-4");
        when(orchestrationRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey("tenant-1", OPERATION_CODE, "idem-4"))
                .thenReturn(Optional.of(orchestration));
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onSettlementResult(event);

        verify(orchestrationRecordRepository).save(any(OrchestrationRecordDocument.class));
        verify(invoiceOrchestrationService, never()).markInboxCompleted(any(), any(), any());
    }

    private static OrchestrationRecordDocument orchestration(String tenantId, String idempotencyKey) {
        OrchestrationRecordDocument document = new OrchestrationRecordDocument();
        document.setOrchestrationId("ORCH-1");
        document.setTenantId(tenantId);
        document.setOperationCode(OPERATION_CODE);
        document.setIdempotencyKey(idempotencyKey);
        document.setStatus(OrchestrationStatus.INVOICE_GENERATED);
        document.setUpdatedAt(Instant.parse("2026-02-21T00:00:00Z"));
        return document;
    }
}
