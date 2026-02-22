package com.mustafabulu.billing.paymentservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.PaymentCompensationRequestedEvent;
import com.mustafabulu.billing.common.events.PaymentCompensationResultEvent;
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

    @Test
    void shouldPublishCompensationResultEvent() {
        PaymentCompensationRequestedEvent event = new PaymentCompensationRequestedEvent(
                "evt-3",
                "tenant-1",
                "ORCH-3",
                "idem-3",
                "TX-3",
                "SETTLEMENT_FAILED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        PaymentResult compensationResult = new PaymentResult(
                "TX-3",
                "INV-3",
                new BigDecimal("20.00"),
                "USD",
                "COMPENSATED",
                "COMPENSATED-idem-3",
                Instant.parse("2026-02-21T00:00:01Z")
        );
        when(paymentApplicationService.compensate("tenant-1", "idem-3", "TX-3")).thenReturn(compensationResult);

        listener.onPaymentCompensationRequested(event);

        ArgumentCaptor<PaymentCompensationResultEvent> captor = ArgumentCaptor.forClass(PaymentCompensationResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_COMPENSATION_RESULT), eq("ORCH-3"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("COMPENSATED");
    }

    @Test
    void shouldPublishFailedCompensationResultWhenServiceReturnsNonCompensatedStatus() {
        PaymentCompensationRequestedEvent event = new PaymentCompensationRequestedEvent(
                "evt-4",
                "tenant-1",
                "ORCH-4",
                "idem-4",
                "TX-4",
                "SETTLEMENT_FAILED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        PaymentResult compensationResult = new PaymentResult(
                "TX-4",
                "INV-4",
                new BigDecimal("20.00"),
                "USD",
                "SUCCESS",
                "unexpected",
                Instant.parse("2026-02-21T00:00:01Z")
        );
        when(paymentApplicationService.compensate("tenant-1", "idem-4", "TX-4")).thenReturn(compensationResult);

        listener.onPaymentCompensationRequested(event);

        ArgumentCaptor<PaymentCompensationResultEvent> captor = ArgumentCaptor.forClass(PaymentCompensationResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_COMPENSATION_RESULT), eq("ORCH-4"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FAILED");
    }

    @Test
    void shouldPublishFailedCompensationResultWhenCompensationThrows() {
        PaymentCompensationRequestedEvent event = new PaymentCompensationRequestedEvent(
                "evt-5",
                "tenant-1",
                "ORCH-5",
                "idem-5",
                "TX-5",
                "SETTLEMENT_FAILED",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        when(paymentApplicationService.compensate("tenant-1", "idem-5", "TX-5"))
                .thenThrow(new RuntimeException("compensation down"));

        listener.onPaymentCompensationRequested(event);

        ArgumentCaptor<PaymentCompensationResultEvent> captor = ArgumentCaptor.forClass(PaymentCompensationResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_COMPENSATION_RESULT), eq("ORCH-5"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FAILED");
        assertThat(captor.getValue().reason()).contains("compensation down");
    }
}
