package com.mustafabulu.billing.invoicebatchservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventDocument;
import com.mustafabulu.billing.invoicebatchservice.persistence.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxPublisherJobTests {

    private final OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
    private final OutboxPublisherJob outboxPublisherJob = new OutboxPublisherJob(outboxEventRepository);

    @Test
    void shouldSkipPublishingWhenDisabled() {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", false);

        outboxPublisherJob.publishPendingOutboxEvents();

        verify(outboxEventRepository, never()).findByStatusOrderByCreatedAtAsc(any(), any(PageRequest.class));
    }

    @Test
    void shouldPublishPendingEventsWhenEnabled() {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", true);
        ReflectionTestUtils.setField(outboxPublisherJob, "batchSize", 10);
        OutboxEventDocument first = newEvent("event-1");
        OutboxEventDocument second = newEvent("event-2");
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("NEW"), any(PageRequest.class)))
                .thenReturn(List.of(first, second));
        when(outboxEventRepository.save(any(OutboxEventDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPublisherJob.publishPendingOutboxEvents();

        ArgumentCaptor<OutboxEventDocument> captor = ArgumentCaptor.forClass(OutboxEventDocument.class);
        verify(outboxEventRepository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(saved -> {
                    assertThat(saved.getAttemptCount()).isEqualTo(1);
                    assertThat(saved.getStatus()).isEqualTo("SENT");
                    assertThat(saved.getPublishedAt()).isNotNull();
                    assertThat(saved.getLastError()).isNull();
                });
    }

    @Test
    void shouldMarkEventFailedWhenPublishingThrows() {
        ReflectionTestUtils.setField(outboxPublisherJob, "enabled", true);
        ReflectionTestUtils.setField(outboxPublisherJob, "batchSize", 1);
        OutboxEventDocument event = newEvent("event-fail");
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("NEW"), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any(OutboxEventDocument.class)))
                .thenThrow(new RuntimeException("publisher down"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        outboxPublisherJob.publishPendingOutboxEvents();

        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("publisher down");
    }

    private static OutboxEventDocument newEvent(String eventId) {
        OutboxEventDocument event = new OutboxEventDocument();
        event.setEventId(eventId);
        event.setEventType("ORCHESTRATION_COMPLETED");
        event.setOrchestrationId("orch-" + eventId);
        event.setPayload("{\"invoiceId\":\"INV-1\"}");
        event.setStatus("NEW");
        event.setAttemptCount(0);
        event.setCreatedAt(Instant.parse("2026-02-21T00:00:00Z"));
        return event;
    }
}
