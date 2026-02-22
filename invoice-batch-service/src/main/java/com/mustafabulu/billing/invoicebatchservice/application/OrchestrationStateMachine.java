package com.mustafabulu.billing.invoicebatchservice.application;

import com.mustafabulu.billing.invoicebatchservice.persistence.OrchestrationStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class OrchestrationStateMachine {

    private static final Map<OrchestrationStatus, Set<OrchestrationStatus>> TRANSITIONS =
            new EnumMap<>(OrchestrationStatus.class);

    static {
        TRANSITIONS.put(OrchestrationStatus.RECEIVED, EnumSet.of(
                OrchestrationStatus.INVOICE_GENERATED,
                OrchestrationStatus.TIMED_OUT,
                OrchestrationStatus.FAILED
        ));
        TRANSITIONS.put(OrchestrationStatus.INVOICE_GENERATED, EnumSet.of(
                OrchestrationStatus.PAYMENT_COMPLETED,
                OrchestrationStatus.FAILED,
                OrchestrationStatus.TIMED_OUT
        ));
        TRANSITIONS.put(OrchestrationStatus.PAYMENT_COMPLETED, EnumSet.of(
                OrchestrationStatus.SETTLEMENT_COMPLETED,
                OrchestrationStatus.COMPENSATION_REQUIRED,
                OrchestrationStatus.COMPENSATION_IN_PROGRESS,
                OrchestrationStatus.TIMED_OUT
        ));
        TRANSITIONS.put(OrchestrationStatus.COMPENSATION_REQUIRED, EnumSet.of(
                OrchestrationStatus.COMPENSATION_IN_PROGRESS,
                OrchestrationStatus.FAILED
        ));
        TRANSITIONS.put(OrchestrationStatus.COMPENSATION_IN_PROGRESS, EnumSet.of(
                OrchestrationStatus.COMPENSATED,
                OrchestrationStatus.FAILED
        ));
    }

    private OrchestrationStateMachine() {
    }

    static boolean canTransition(OrchestrationStatus from, OrchestrationStatus to) {
        if (from == to) {
            return true;
        }
        Set<OrchestrationStatus> allowedTargets = TRANSITIONS.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }

    static boolean isTerminal(OrchestrationStatus status) {
        return status == OrchestrationStatus.SETTLEMENT_COMPLETED
                || status == OrchestrationStatus.COMPENSATED
                || status == OrchestrationStatus.FAILED
                || status == OrchestrationStatus.TIMED_OUT;
    }
}
