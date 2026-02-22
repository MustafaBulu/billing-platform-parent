package com.mustafabulu.billing.paymentservice.application;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentCompensationRequestedEvent;
import com.mustafabulu.billing.common.events.PaymentCompensationResultEvent;
import com.mustafabulu.billing.common.events.PaymentRequestedEvent;
import com.mustafabulu.billing.common.events.PaymentResultEvent;
import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_COMPENSATED = "COMPENSATED";

    private final PaymentApplicationService paymentApplicationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventListener(PaymentApplicationService paymentApplicationService,
                                KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentApplicationService = paymentApplicationService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.mustafabulu.billing.common.events.PaymentRequestedEvent"
    )
    public void onPaymentRequested(PaymentRequestedEvent event) {
        if (!hasRequiredCorrelation(event.tenantId(), event.orchestrationId(), event.idempotencyKey())) {
            log.warn("payment_requested_ignored reason=missing_correlation tenantId={} orchestrationId={} idempotencyKey={}",
                    event.tenantId(), event.orchestrationId(), event.idempotencyKey());
            return;
        }
        PaymentResultEvent resultEvent;
        try {
            PaymentResult result = paymentApplicationService.process(new ProcessPaymentRequest(
                    event.tenantId(),
                    event.invoiceId(),
                    event.idempotencyKey(),
                    event.amount(),
                    event.currency()
            ));
            resultEvent = new PaymentResultEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    event.orchestrationId(),
                    event.idempotencyKey(),
                    event.invoiceId(),
                    result.transactionId(),
                    result.amount(),
                    result.currency(),
                    result.status(),
                    result.providerReference(),
                    result.processedAt()
            );
        } catch (RuntimeException exception) {
            resultEvent = new PaymentResultEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    event.orchestrationId(),
                    event.idempotencyKey(),
                    event.invoiceId(),
                    null,
                    event.amount(),
                    event.currency(),
                    STATUS_FAILED,
                    exception.getMessage(),
                    Instant.now()
            );
        }

        kafkaTemplate.send(KafkaTopics.PAYMENT_RESULT, event.orchestrationId(), resultEvent);
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPENSATION_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.mustafabulu.billing.common.events.PaymentCompensationRequestedEvent"
    )
    public void onPaymentCompensationRequested(PaymentCompensationRequestedEvent event) {
        if (!hasRequiredCorrelation(event.tenantId(), event.orchestrationId(), event.idempotencyKey())) {
            log.warn("payment_compensation_requested_ignored reason=missing_correlation tenantId={} orchestrationId={} idempotencyKey={}",
                    event.tenantId(), event.orchestrationId(), event.idempotencyKey());
            return;
        }
        PaymentCompensationResultEvent resultEvent;
        try {
            PaymentResult result = paymentApplicationService.compensate(
                    event.tenantId(),
                    event.idempotencyKey(),
                    event.transactionId()
            );
            resultEvent = new PaymentCompensationResultEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    event.orchestrationId(),
                    event.idempotencyKey(),
                    event.transactionId(),
                    STATUS_COMPENSATED.equalsIgnoreCase(result.status()) ? STATUS_COMPENSATED : STATUS_FAILED,
                    result.providerReference(),
                    result.processedAt()
            );
        } catch (RuntimeException exception) {
            resultEvent = new PaymentCompensationResultEvent(
                    "EVT-" + UUID.randomUUID(),
                    event.tenantId(),
                    event.orchestrationId(),
                    event.idempotencyKey(),
                    event.transactionId(),
                    STATUS_FAILED,
                    exception.getMessage(),
                    Instant.now()
            );
        }
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPENSATION_RESULT, event.orchestrationId(), resultEvent);
    }

    private boolean hasRequiredCorrelation(String tenantId, String orchestrationId, String idempotencyKey) {
        return isNotBlank(tenantId) && isNotBlank(orchestrationId) && isNotBlank(idempotencyKey);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
