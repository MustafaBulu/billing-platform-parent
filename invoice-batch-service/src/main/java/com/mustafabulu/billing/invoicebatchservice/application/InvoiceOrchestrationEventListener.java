package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentCompensationRequestedEvent;
import com.mustafabulu.billing.common.events.PaymentCompensationResultEvent;
import com.mustafabulu.billing.common.events.PaymentResultEvent;
import com.mustafabulu.billing.common.events.SettlementRequestedEvent;
import com.mustafabulu.billing.common.events.SettlementResultEvent;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InvoiceOrchestrationEventListener {
    private static final Logger log = LoggerFactory.getLogger(InvoiceOrchestrationEventListener.class);

    private static final String OPERATION_CODE = "INVOICE_GENERATE_AND_SETTLE";

    private final OrchestrationRecordRepository orchestrationRecordRepository;
    private final InvoiceOrchestrationService invoiceOrchestrationService;

    public InvoiceOrchestrationEventListener(OrchestrationRecordRepository orchestrationRecordRepository,
                                             InvoiceOrchestrationService invoiceOrchestrationService) {
        this.orchestrationRecordRepository = orchestrationRecordRepository;
        this.invoiceOrchestrationService = invoiceOrchestrationService;
    }

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_RESULT,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.mustafabulu.billing.common.events.PaymentResultEvent"
    )
    public void onPaymentResult(PaymentResultEvent event) {
        if (!hasRequiredCorrelation(event.tenantId(), event.orchestrationId(), event.idempotencyKey())) {
            log.warn("payment_result_ignored reason=missing_correlation tenantId={} orchestrationId={} idempotencyKey={}",
                    event.tenantId(), event.orchestrationId(), event.idempotencyKey());
            return;
        }
        OrchestrationRecordDocument orchestration = orchestrationRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(event.tenantId(), OPERATION_CODE, event.idempotencyKey())
                .orElse(null);
        if (orchestration == null) {
            return;
        }
        if (OrchestrationStateMachine.isTerminal(orchestration.getStatus())) {
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(event.status())) {
            if (!OrchestrationStateMachine.canTransition(orchestration.getStatus(), OrchestrationStatus.PAYMENT_COMPLETED)) {
                return;
            }
            orchestration.setPaymentTransactionId(event.transactionId());
            orchestration.setStatus(OrchestrationStatus.PAYMENT_COMPLETED);
            orchestration.setFailureReason(null);
            orchestration.setUpdatedAt(Instant.now());
            orchestrationRecordRepository.save(orchestration);

            SettlementRequestedEvent settlementRequestedEvent = new SettlementRequestedEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    orchestration.getOrchestrationId(),
                    event.idempotencyKey(),
                    event.invoiceId(),
                    event.transactionId(),
                    event.amount(),
                    event.currency(),
                    event.status(),
                    Instant.now()
            );
            invoiceOrchestrationService.writeOutboxEvent(orchestration, "SETTLEMENT_REQUESTED", settlementRequestedEvent);
            return;
        }

        invoiceOrchestrationService.markFailed(orchestration, "PAYMENT_FAILED");
    }

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.SETTLEMENT_RESULT,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.mustafabulu.billing.common.events.SettlementResultEvent"
    )
    public void onSettlementResult(SettlementResultEvent event) {
        if (!hasRequiredCorrelation(event.tenantId(), event.orchestrationId(), event.idempotencyKey())) {
            log.warn("settlement_result_ignored reason=missing_correlation tenantId={} orchestrationId={} idempotencyKey={}",
                    event.tenantId(), event.orchestrationId(), event.idempotencyKey());
            return;
        }
        OrchestrationRecordDocument orchestration = orchestrationRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(event.tenantId(), OPERATION_CODE, event.idempotencyKey())
                .orElse(null);
        if (orchestration == null) {
            return;
        }

        orchestration.setSettlementSagaId(event.sagaId());
        orchestration.setUpdatedAt(Instant.now());
        if ("SETTLED".equalsIgnoreCase(event.status())) {
            if (!OrchestrationStateMachine.canTransition(orchestration.getStatus(), OrchestrationStatus.SETTLEMENT_COMPLETED)) {
                return;
            }
            orchestration.setStatus(OrchestrationStatus.SETTLEMENT_COMPLETED);
            orchestration.setFailureReason(null);
            orchestrationRecordRepository.save(orchestration);
            invoiceOrchestrationService.markInboxCompleted(
                    event.tenantId(),
                    event.idempotencyKey(),
                    orchestration.getOrchestrationId());
            Metrics.counter("platform.saga.completed").increment();
            return;
        }

        if (!OrchestrationStateMachine.canTransition(orchestration.getStatus(), OrchestrationStatus.COMPENSATION_IN_PROGRESS)) {
            return;
        }
        orchestration.setStatus(OrchestrationStatus.COMPENSATION_IN_PROGRESS);
        orchestration.setFailureReason("SETTLEMENT_FAILED");
        orchestrationRecordRepository.save(orchestration);
        PaymentCompensationRequestedEvent compensationRequestedEvent = new PaymentCompensationRequestedEvent(
                "EVT-" + UUID.randomUUID(),
                event.tenantId(),
                event.orchestrationId(),
                event.idempotencyKey(),
                event.paymentTransactionId(),
                "SETTLEMENT_FAILED",
                Instant.now()
        );
        invoiceOrchestrationService.writeOutboxEvent(
                orchestration,
                "PAYMENT_COMPENSATION_REQUESTED",
                compensationRequestedEvent
        );
    }

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPENSATION_RESULT,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.mustafabulu.billing.common.events.PaymentCompensationResultEvent"
    )
    public void onPaymentCompensationResult(PaymentCompensationResultEvent event) {
        if (!hasRequiredCorrelation(event.tenantId(), event.orchestrationId(), event.idempotencyKey())) {
            log.warn("payment_compensation_result_ignored reason=missing_correlation tenantId={} orchestrationId={} idempotencyKey={}",
                    event.tenantId(), event.orchestrationId(), event.idempotencyKey());
            return;
        }
        OrchestrationRecordDocument orchestration = orchestrationRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(event.tenantId(), OPERATION_CODE, event.idempotencyKey())
                .orElse(null);
        if (orchestration == null) {
            return;
        }
        if ("COMPENSATED".equalsIgnoreCase(event.status())
                && OrchestrationStateMachine.canTransition(orchestration.getStatus(), OrchestrationStatus.COMPENSATED)) {
            orchestration.setStatus(OrchestrationStatus.COMPENSATED);
            orchestration.setFailureReason(event.reason());
            orchestration.setUpdatedAt(Instant.now());
            orchestrationRecordRepository.save(orchestration);
            invoiceOrchestrationService.markInboxCompleted(event.tenantId(), event.idempotencyKey(), orchestration.getOrchestrationId());
            Metrics.counter("platform.saga.compensated").increment();
            return;
        }

        orchestration.setStatus(OrchestrationStatus.FAILED);
        orchestration.setFailureReason("COMPENSATION_FAILED");
        orchestration.setUpdatedAt(Instant.now());
        orchestrationRecordRepository.save(orchestration);
        invoiceOrchestrationService.markInboxCompleted(event.tenantId(), event.idempotencyKey(), orchestration.getOrchestrationId());
        Metrics.counter("platform.saga.failed").increment();
    }

    private boolean hasRequiredCorrelation(String tenantId, String orchestrationId, String idempotencyKey) {
        return isNotBlank(tenantId) && isNotBlank(orchestrationId) && isNotBlank(idempotencyKey);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
