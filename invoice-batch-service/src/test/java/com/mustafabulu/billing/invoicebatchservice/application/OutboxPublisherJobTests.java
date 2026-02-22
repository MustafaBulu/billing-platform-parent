package com.mustafabulu.billing.invoicebatchservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mustafabulu.billing.common.events.PaymentRequestedEvent;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxPublisherJobTests {

    private final OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final OutboxPublisherJob outboxPublisherJob = new OutboxPublisherJob(
            outboxEventRepository, kafkaTemplate, objectMapper);

    @Test
    void shouldSkipPublishingWhenDisabled() {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", false);

        outboxPublisherJob.publishPendingOutboxEvents();

        verify(outboxEventRepository, never()).findByStatusInOrderByCreatedAtAsc(any(), any(PageRequest.class));
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldPublishPaymentRequestedEventsWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", true);
        ReflectionTestUtils.setField(outboxPublisherJob, "batchSize", 10);
        ReflectionTestUtils.setField(outboxPublisherJob, "maxAttempts", 3);
        OutboxEventDocument event = newPaymentRequestedEvent("event-1", objectMapper);
        when(outboxEventRepository.findByStatusInOrderByCreatedAtAsc(any(), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPublisherJob.publishPendingOutboxEvents();

        verify(kafkaTemplate).send(eq("billing.payment.requested"), eq(event.getOrchestrationId()), any(PaymentRequestedEvent.class));
        ArgumentCaptor<OutboxEventDocument> captor = ArgumentCaptor.forClass(OutboxEventDocument.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void shouldMarkEventFailedWhenKafkaSendThrows() throws Exception {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", true);
        ReflectionTestUtils.setField(outboxPublisherJob, "batchSize", 1);
        ReflectionTestUtils.setField(outboxPublisherJob, "maxAttempts", 3);
        OutboxEventDocument event = newPaymentRequestedEvent("event-fail", objectMapper);
        when(outboxEventRepository.findByStatusInOrderByCreatedAtAsc(any(), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("kafka down"));

        outboxPublisherJob.publishPendingOutboxEvents();

        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("kafka down");
    }

    @Test
    void shouldRouteToDeadLetterAfterMaxAttempts() throws Exception {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", true);
        ReflectionTestUtils.setField(outboxPublisherJob, "batchSize", 1);
        ReflectionTestUtils.setField(outboxPublisherJob, "maxAttempts", 1);
        OutboxEventDocument event = newPaymentRequestedEvent("event-dlq", objectMapper);
        when(outboxEventRepository.findByStatusInOrderByCreatedAtAsc(any(), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(eq("billing.payment.requested"), any(), any())).thenThrow(new RuntimeException("kafka down"));

        outboxPublisherJob.publishPendingOutboxEvents();

        assertThat(event.getStatus()).isEqualTo("DEAD_LETTER");
        verify(kafkaTemplate).send(eq("billing.dlq"), eq(event.getOrchestrationId()), any(Map.class));
    }

    private static OutboxEventDocument newPaymentRequestedEvent(String eventId, ObjectMapper objectMapper) throws Exception {
        PaymentRequestedEvent payload = new PaymentRequestedEvent(
                eventId,
                "tenant-1",
                "orch-" + eventId,
                "idem-1",
                "INV-1",
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-02-21T00:00:00Z")
        );

        OutboxEventDocument event = new OutboxEventDocument();
        event.setEventId(eventId);
        event.setEventType("PAYMENT_REQUESTED");
        event.setOrchestrationId("orch-" + eventId);
        event.setPayload(objectMapper.writeValueAsString(payload));
        event.setStatus("NEW");
        event.setAttemptCount(0);
        event.setCreatedAt(Instant.parse("2026-02-21T00:00:00Z"));
        return event;
    }
}
