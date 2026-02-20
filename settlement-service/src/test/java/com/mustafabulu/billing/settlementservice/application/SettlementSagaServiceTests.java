package com.mustafabulu.billing.settlementservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SettlementSagaServiceTests {

    private final SettlementSagaService settlementSagaService = new SettlementSagaService();

    @Test
    void shouldSettleWhenPaymentSuccessful() {
        StartSettlementRequest request = new StartSettlementRequest(
                "tenant-1",
                "INV-1",
                "TX-1",
                BigDecimal.TEN,
                "USD",
                "SUCCESS"
        );

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
                BigDecimal.TEN,
                "USD",
                "FAILED"
        );

        SettlementSaga saga = settlementSagaService.start(request);

        assertThat(saga.status()).isEqualTo(SettlementStatus.FAILED);
        assertThat(saga.transitions()).containsExactly(
                SettlementStatus.STARTED,
                SettlementStatus.FAILED
        );
    }
}
