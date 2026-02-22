package com.mustafabulu.billing.paymentservice.application;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentRequestedEvent;
import com.mustafabulu.billing.common.events.PaymentResultEvent;
import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

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
                    "FAILED",
                    exception.getMessage(),
                    Instant.now()
            );
        }

        kafkaTemplate.send(KafkaTopics.PAYMENT_RESULT, event.orchestrationId(), resultEvent);
    }
}
