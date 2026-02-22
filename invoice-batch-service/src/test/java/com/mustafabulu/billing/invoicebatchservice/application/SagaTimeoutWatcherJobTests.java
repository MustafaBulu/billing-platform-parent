package com.mustafabulu.billing.invoicebatchservice.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class SagaTimeoutWatcherJobTests {

    private final OrchestrationRecordRepository orchestrationRecordRepository = Mockito.mock(OrchestrationRecordRepository.class);
    private final InvoiceOrchestrationService invoiceOrchestrationService = Mockito.mock(InvoiceOrchestrationService.class);
    private final SagaTimeoutWatcherJob watcherJob = new SagaTimeoutWatcherJob(
            orchestrationRecordRepository, invoiceOrchestrationService);

    @Test
    void shouldRequestCompensationForTimedOutPaymentCompletedSaga() {
        ReflectionTestUtils.setField(watcherJob, "enabled", true);
        ReflectionTestUtils.setField(watcherJob, "timeoutThresholdSeconds", 60L);
        OrchestrationRecordDocument orchestration = orchestration(OrchestrationStatus.PAYMENT_COMPLETED, "idem-1");
        when(orchestrationRecordRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(orchestration));
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        watcherJob.markTimedOutSagas();

        verify(orchestrationRecordRepository).save(any(OrchestrationRecordDocument.class));
        verify(invoiceOrchestrationService).writeOutboxEvent(any(OrchestrationRecordDocument.class),
                Mockito.eq("PAYMENT_COMPENSATION_REQUESTED"), any());
    }

    @Test
    void shouldMarkPrePaymentSagaTimedOut() {
        ReflectionTestUtils.setField(watcherJob, "enabled", true);
        ReflectionTestUtils.setField(watcherJob, "timeoutThresholdSeconds", 60L);
        OrchestrationRecordDocument orchestration = orchestration(OrchestrationStatus.INVOICE_GENERATED, "idem-2");
        when(orchestrationRecordRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(orchestration));
        when(orchestrationRecordRepository.save(any(OrchestrationRecordDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        watcherJob.markTimedOutSagas();

        verify(orchestrationRecordRepository).save(any(OrchestrationRecordDocument.class));
        verify(invoiceOrchestrationService).writeOutboxEvent(any(OrchestrationRecordDocument.class),
                Mockito.eq("ORCHESTRATION_TIMEOUT"), Mockito.eq("SAGA_TIMEOUT"));
    }

    private static OrchestrationRecordDocument orchestration(OrchestrationStatus status, String idempotencyKey) {
        OrchestrationRecordDocument document = new OrchestrationRecordDocument();
        document.setTenantId("tenant-1");
        document.setOrchestrationId("ORCH-1");
        document.setOperationCode("INVOICE_GENERATE_AND_SETTLE");
        document.setIdempotencyKey(idempotencyKey);
        document.setPaymentTransactionId("TX-1");
        document.setStatus(status);
        document.setUpdatedAt(Instant.parse("2026-02-21T00:00:00Z"));
        return document;
    }
}
