package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.common.events.PaymentCompensationRequestedEvent;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SagaTimeoutWatcherJob {

    private final OrchestrationRecordRepository orchestrationRecordRepository;
    private final InvoiceOrchestrationService invoiceOrchestrationService;

    @Value("${platform.saga.timeout.enabled:true}")
    private boolean enabled;

    @Value("${platform.saga.timeout.threshold-seconds:120}")
    private long timeoutThresholdSeconds;

    public SagaTimeoutWatcherJob(OrchestrationRecordRepository orchestrationRecordRepository,
                                 InvoiceOrchestrationService invoiceOrchestrationService) {
        this.orchestrationRecordRepository = orchestrationRecordRepository;
        this.invoiceOrchestrationService = invoiceOrchestrationService;
    }

    @Scheduled(fixedDelayString = "${platform.saga.timeout.fixed-delay-ms:30000}")
    @Transactional
    public void markTimedOutSagas() {
        if (!enabled) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(Math.max(timeoutThresholdSeconds, 30));
        List<OrchestrationRecordDocument> staleOrchestrations = orchestrationRecordRepository
                .findByStatusInAndUpdatedAtBefore(
                        List.of(
                                OrchestrationStatus.RECEIVED,
                                OrchestrationStatus.INVOICE_GENERATED,
                                OrchestrationStatus.PAYMENT_COMPLETED,
                                OrchestrationStatus.COMPENSATION_REQUIRED
                        ),
                        cutoff
                );

        for (OrchestrationRecordDocument orchestration : staleOrchestrations) {
            handleTimedOutOrchestration(orchestration);
        }
    }

    private void handleTimedOutOrchestration(OrchestrationRecordDocument orchestration) {
        if (OrchestrationStateMachine.isTerminal(orchestration.getStatus())) {
            return;
        }
        if (orchestration.getStatus() == OrchestrationStatus.PAYMENT_COMPLETED
                || orchestration.getStatus() == OrchestrationStatus.COMPENSATION_REQUIRED) {
            requestCompensation(orchestration, "SAGA_TIMEOUT");
            Metrics.counter("platform.saga.timeout").increment();
            return;
        }

        orchestration.setStatus(OrchestrationStatus.TIMED_OUT);
        orchestration.setFailureReason("SAGA_TIMEOUT");
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        invoiceOrchestrationService.markInboxCompleted(
                orchestration.getTenantId(),
                orchestration.getIdempotencyKey(),
                orchestration.getOrchestrationId()
        );
        invoiceOrchestrationService.writeOutboxEvent(orchestration, "ORCHESTRATION_TIMEOUT", "SAGA_TIMEOUT");
        Metrics.counter("platform.saga.timeout").increment();
    }

    private void requestCompensation(OrchestrationRecordDocument orchestration, String reason) {
        if (!OrchestrationStateMachine.canTransition(orchestration.getStatus(), OrchestrationStatus.COMPENSATION_IN_PROGRESS)) {
            return;
        }
        orchestration.setStatus(OrchestrationStatus.COMPENSATION_IN_PROGRESS);
        orchestration.setFailureReason(reason);
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);

        PaymentCompensationRequestedEvent event = new PaymentCompensationRequestedEvent(
                "EVT-" + UUID.randomUUID(),
                orchestration.getTenantId(),
                orchestration.getOrchestrationId(),
                orchestration.getIdempotencyKey(),
                orchestration.getPaymentTransactionId(),
                reason,
                Instant.now()
        );
        invoiceOrchestrationService.writeOutboxEvent(orchestration, "PAYMENT_COMPENSATION_REQUESTED", event);
    }
}
