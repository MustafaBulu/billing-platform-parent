package com.mustafabulu.billing.invoicebatchservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import org.junit.jupiter.api.Test;

class OrchestrationStateMachineTests {

    @Test
    void shouldAllowValidForwardTransition() {
        assertThat(OrchestrationStateMachine.canTransition(
                OrchestrationStatus.INVOICE_GENERATED,
                OrchestrationStatus.PAYMENT_COMPLETED)).isTrue();
    }

    @Test
    void shouldRejectInvalidBackwardTransition() {
        assertThat(OrchestrationStateMachine.canTransition(
                OrchestrationStatus.SETTLEMENT_COMPLETED,
                OrchestrationStatus.PAYMENT_COMPLETED)).isFalse();
    }

    @Test
    void shouldRecognizeTerminalStatuses() {
        assertThat(OrchestrationStateMachine.isTerminal(OrchestrationStatus.SETTLEMENT_COMPLETED)).isTrue();
        assertThat(OrchestrationStateMachine.isTerminal(OrchestrationStatus.COMPENSATED)).isTrue();
        assertThat(OrchestrationStateMachine.isTerminal(OrchestrationStatus.INVOICE_GENERATED)).isFalse();
    }
}
