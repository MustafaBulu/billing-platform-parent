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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
}
