package com.mustafabulu.billing.paymentservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentRequestedEvent;
import com.mustafabulu.billing.common.events.PaymentResultEvent;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

class PaymentEventListenerTests {

    private final PaymentApplicationService paymentApplicationService = Mockito.mock(PaymentApplicationService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final PaymentEventListener listener = new PaymentEventListener(paymentApplicationService, kafkaTemplate);

    @Test
    void shouldPublishSuccessPaymentResultEvent() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                "evt-1",
                "tenant-1",
                "ORCH-1",
                "idem-1",
                "INV-1",
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        PaymentResult paymentResult = new PaymentResult(
                "TX-1",
                "INV-1",
                new BigDecimal("10.00"),
                "USD",
                "SUCCESS",
                "APPROVED-ref",
                Instant.parse("2026-02-21T00:00:01Z")
        );
        when(paymentApplicationService.process(any())).thenReturn(paymentResult);

        listener.onPaymentRequested(event);

        ArgumentCaptor<PaymentResultEvent> captor = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_RESULT), eq("ORCH-1"), captor.capture());
        PaymentResultEvent published = captor.getValue();
        assertThat(published.status()).isEqualTo("SUCCESS");
        assertThat(published.transactionId()).isEqualTo("TX-1");
    }

    @Test
    void shouldPublishFailedPaymentResultWhenProcessingThrows() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                "evt-2",
                "tenant-1",
                "ORCH-2",
                "idem-2",
                "INV-2",
                new BigDecimal("20.00"),
                "USD",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        when(paymentApplicationService.process(any())).thenThrow(new RuntimeException("provider down"));

        listener.onPaymentRequested(event);

        ArgumentCaptor<PaymentResultEvent> captor = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_RESULT), eq("ORCH-2"), captor.capture());
        PaymentResultEvent published = captor.getValue();
        assertThat(published.status()).isEqualTo("FAILED");
        assertThat(published.transactionId()).isNull();
    }
}
