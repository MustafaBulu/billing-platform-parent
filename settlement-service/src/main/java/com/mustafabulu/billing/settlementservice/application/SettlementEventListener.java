package com.mustafabulu.billing.settlementservice.application;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.SettlementRequestedEvent;
import com.mustafabulu.billing.common.events.SettlementResultEvent;
import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SettlementEventListener {
    private static final Logger log = LoggerFactory.getLogger(SettlementEventListener.class);

    private final SettlementSagaService settlementSagaService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SettlementEventListener(SettlementSagaService settlementSagaService,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        this.settlementSagaService = settlementSagaService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = KafkaTopics.SETTLEMENT_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.mustafabulu.billing.common.events.SettlementRequestedEvent"
    )
    public void onSettlementRequested(SettlementRequestedEvent event) {
        if (!hasRequiredCorrelation(event.tenantId(), event.orchestrationId(), event.idempotencyKey())) {
            log.warn("settlement_requested_ignored reason=missing_correlation tenantId={} orchestrationId={} idempotencyKey={}",
                    event.tenantId(), event.orchestrationId(), event.idempotencyKey());
            return;
        }
        SettlementResultEvent resultEvent;
        try {
            SettlementSaga saga = settlementSagaService.start(new StartSettlementRequest(
                    event.tenantId(),
                    event.invoiceId(),
                    event.paymentTransactionId(),
                    event.idempotencyKey(),
                    event.amount(),
                    event.currency(),
                    event.paymentStatus()
            ));
            resultEvent = new SettlementResultEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    event.orchestrationId(),
                    event.idempotencyKey(),
                    saga.sagaId(),
                    event.invoiceId(),
                    event.paymentTransactionId(),
                    saga.amount(),
                    saga.currency(),
                    saga.status().name(),
                    saga.updatedAt()
            );
        } catch (RuntimeException exception) {
            resultEvent = new SettlementResultEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    event.orchestrationId(),
                    event.idempotencyKey(),
                    null,
                    event.invoiceId(),
                    event.paymentTransactionId(),
                    event.amount(),
                    event.currency(),
                    "FAILED",
                    Instant.now()
            );
        }

        kafkaTemplate.send(KafkaTopics.SETTLEMENT_RESULT, event.orchestrationId(), resultEvent);
    }

    private boolean hasRequiredCorrelation(String tenantId, String orchestrationId, String idempotencyKey) {
        return isNotBlank(tenantId) && isNotBlank(orchestrationId) && isNotBlank(idempotencyKey);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
