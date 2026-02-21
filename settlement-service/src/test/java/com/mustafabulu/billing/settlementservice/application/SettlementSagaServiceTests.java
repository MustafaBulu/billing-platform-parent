package com.mustafabulu.billing.settlementservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import com.mustafabulu.billing.settlementservice.persistence.SettlementSagaDocument;
import com.mustafabulu.billing.settlementservice.persistence.SettlementSagaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

class SettlementSagaServiceTests {

    private final SettlementSagaRepository settlementSagaRepository = Mockito.mock(SettlementSagaRepository.class);
    private final SettlementSagaService settlementSagaService = new SettlementSagaService(settlementSagaRepository);

    @Test
    void shouldSettleWhenPaymentSuccessful() {
        StartSettlementRequest request = new StartSettlementRequest(
                "tenant-1",
                "INV-1",
                "TX-1",
                "idem-1",
                BigDecimal.TEN,
                "USD",
                "SUCCESS"
        );
        when(settlementSagaRepository.findByTenantIdAndOperationCodeAndIdempotencyKey("tenant-1", "SETTLEMENT_START",
                "tenant-1:SETTLEMENT_START:idem-1")).thenReturn(Optional.empty());
        when(settlementSagaRepository.save(any(SettlementSagaDocument.class))).thenAnswer(invocation -> {
            SettlementSagaDocument document = invocation.getArgument(0);
            document.setUpdatedAt(Instant.now());
            return document;
        });

        SettlementSaga saga = settlementSagaService.start(request);

        assertThat(saga.status()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(saga.transitions()).containsExactly(
                SettlementStatus.STARTED,
                SettlementStatus.PAYMENT_CONFIRMED,
                SettlementStatus.SETTLED
        );
    }

    @Test
    void shouldFailWhenPaymentNotSuccessful() {
        StartSettlementRequest request = new StartSettlementRequest(
                "tenant-1",
                "INV-2",
                "TX-2",
                "idem-2",
                BigDecimal.TEN,
                "USD",
                "FAILED"
        );
        when(settlementSagaRepository.findByTenantIdAndOperationCodeAndIdempotencyKey("tenant-1", "SETTLEMENT_START",
                "tenant-1:SETTLEMENT_START:idem-2")).thenReturn(Optional.empty());
        when(settlementSagaRepository.save(any(SettlementSagaDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementSaga saga = settlementSagaService.start(request);

        assertThat(saga.status()).isEqualTo(SettlementStatus.FAILED);
        assertThat(saga.transitions()).containsExactly(
                SettlementStatus.STARTED,
                SettlementStatus.FAILED
        );
    }

    @Test
    void shouldReturnExistingSagaForSameIdempotencyKey() {
        StartSettlementRequest request = new StartSettlementRequest(
                "tenant-1", "INV-3", "TX-3", "idem-3", BigDecimal.TEN, "USD", "SUCCESS");
        SettlementSagaDocument existing = new SettlementSagaDocument();
        existing.setSagaId("SAGA-existing");
        existing.setTenantId("tenant-1");
        existing.setOperationCode("SETTLEMENT_START");
        existing.setIdempotencyKey("tenant-1:SETTLEMENT_START:idem-3");
        existing.setInvoiceId("INV-3");
        existing.setPaymentTransactionId("TX-3");
        existing.setAmount(BigDecimal.TEN);
        existing.setCurrency("USD");
        existing.setStatus(SettlementStatus.SETTLED);
        existing.setTransitions(List.of(SettlementStatus.STARTED, SettlementStatus.PAYMENT_CONFIRMED, SettlementStatus.SETTLED));
        existing.setUpdatedAt(Instant.parse("2026-02-21T00:00:00Z"));

        when(settlementSagaRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "SETTLEMENT_START", "tenant-1:SETTLEMENT_START:idem-3"))
                .thenReturn(Optional.of(existing));

        SettlementSaga saga = settlementSagaService.start(request);

        assertThat(saga.sagaId()).isEqualTo("SAGA-existing");
        assertThat(saga.status()).isEqualTo(SettlementStatus.SETTLED);
    }

    @Test
    void shouldFallbackToExistingSagaOnDuplicateSave() {
        StartSettlementRequest request = new StartSettlementRequest(
                "tenant-1", "INV-4", "TX-4", "idem-4", BigDecimal.TEN, "USD", "SUCCESS");
        SettlementSagaDocument existing = new SettlementSagaDocument();
        existing.setSagaId("SAGA-dup");
        existing.setTenantId("tenant-1");
        existing.setOperationCode("SETTLEMENT_START");
        existing.setIdempotencyKey("tenant-1:SETTLEMENT_START:idem-4");
        existing.setInvoiceId("INV-4");
        existing.setPaymentTransactionId("TX-4");
        existing.setAmount(BigDecimal.TEN);
        existing.setCurrency("USD");
        existing.setStatus(SettlementStatus.SETTLED);
        existing.setTransitions(List.of(SettlementStatus.STARTED, SettlementStatus.PAYMENT_CONFIRMED, SettlementStatus.SETTLED));
        existing.setUpdatedAt(Instant.parse("2026-02-21T00:00:00Z"));

        when(settlementSagaRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                "tenant-1", "SETTLEMENT_START", "tenant-1:SETTLEMENT_START:idem-4"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(settlementSagaRepository.save(any(SettlementSagaDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        SettlementSaga saga = settlementSagaService.start(request);

        assertThat(saga.sagaId()).isEqualTo("SAGA-dup");
        assertThat(saga.status()).isEqualTo(SettlementStatus.SETTLED);
    }

    @Test
    void shouldReturnNullWhenSagaNotFoundById() {
        when(settlementSagaRepository.findBySagaId("SAGA-x")).thenReturn(Optional.empty());

        SettlementSaga saga = settlementSagaService.getById("SAGA-x");

        assertThat(saga).isNull();
    }
}
