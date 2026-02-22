package com.mustafabulu.billing.settlementservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.common.events.KafkaTopics;
import com.mustafabulu.billing.common.events.SettlementRequestedEvent;
import com.mustafabulu.billing.common.events.SettlementResultEvent;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

class SettlementEventListenerTests {

    private final SettlementSagaService settlementSagaService = Mockito.mock(SettlementSagaService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final SettlementEventListener listener = new SettlementEventListener(settlementSagaService, kafkaTemplate);

    @Test
    void shouldPublishSettledResultEvent() {
        SettlementRequestedEvent event = new SettlementRequestedEvent(
                "evt-1",
                "tenant-1",
                "ORCH-1",
                "idem-1",
                "INV-1",
                "TX-1",
                new BigDecimal("10.00"),
                "USD",
                "SUCCESS",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        SettlementSaga saga = new SettlementSaga(
                "SAGA-1",
                "tenant-1",
                "INV-1",
                "TX-1",
                new BigDecimal("10.00"),
                "USD",
                SettlementStatus.SETTLED,
                List.of(SettlementStatus.STARTED, SettlementStatus.PAYMENT_CONFIRMED, SettlementStatus.SETTLED),
                Instant.parse("2026-02-21T00:00:01Z")
        );
        when(settlementSagaService.start(any())).thenReturn(saga);

        listener.onSettlementRequested(event);

        ArgumentCaptor<SettlementResultEvent> captor = ArgumentCaptor.forClass(SettlementResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.SETTLEMENT_RESULT), eq("ORCH-1"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("SETTLED");
        assertThat(captor.getValue().sagaId()).isEqualTo("SAGA-1");
    }

    @Test
    void shouldPublishFailedResultEventWhenSettlementThrows() {
        SettlementRequestedEvent event = new SettlementRequestedEvent(
                "evt-2",
                "tenant-1",
                "ORCH-2",
                "idem-2",
                "INV-2",
                "TX-2",
                new BigDecimal("20.00"),
                "USD",
                "SUCCESS",
                Instant.parse("2026-02-21T00:00:00Z")
        );
        when(settlementSagaService.start(any())).thenThrow(new RuntimeException("db error"));

        listener.onSettlementRequested(event);

        ArgumentCaptor<SettlementResultEvent> captor = ArgumentCaptor.forClass(SettlementResultEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.SETTLEMENT_RESULT), eq("ORCH-2"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("FAILED");
        assertThat(captor.getValue().sagaId()).isNull();
    }
}
