package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentResultEvent;
import com.mustafabulu.billing.common.events.SettlementRequestedEvent;
import com.mustafabulu.billing.common.events.SettlementResultEvent;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationRecordRepository;
import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InvoiceOrchestrationEventListener {

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
        OrchestrationRecordDocument orchestration = orchestrationRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(event.tenantId(), OPERATION_CODE, event.idempotencyKey())
                .orElse(null);
        if (orchestration == null) {
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(event.status())) {
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
        OrchestrationRecordDocument orchestration = orchestrationRecordRepository
                .findByTenantIdAndOperationCodeAndIdempotencyKey(event.tenantId(), OPERATION_CODE, event.idempotencyKey())
                .orElse(null);
        if (orchestration == null) {
            return;
        }

        orchestration.setSettlementSagaId(event.sagaId());
        orchestration.setUpdatedAt(Instant.now());
        if ("SETTLED".equalsIgnoreCase(event.status())) {
            orchestration.setStatus(OrchestrationStatus.SETTLEMENT_COMPLETED);
            orchestration.setFailureReason(null);
            orchestrationRecordRepository.save(orchestration);
            invoiceOrchestrationService.markInboxCompleted(
                    event.tenantId(),
                    event.idempotencyKey(),
                    orchestration.getOrchestrationId());
            return;
        }

        orchestration.setStatus(OrchestrationStatus.COMPENSATION_REQUIRED);
        orchestration.setFailureReason("SETTLEMENT_FAILED");
        orchestrationRecordRepository.save(orchestration);
    }
}
