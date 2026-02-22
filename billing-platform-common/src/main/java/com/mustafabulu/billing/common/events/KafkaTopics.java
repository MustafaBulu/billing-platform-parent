package com.mustafabulu.billing.common.events;

public final class KafkaTopics {

    public static final String PAYMENT_REQUESTED = "billing.payment.requested";
    public static final String PAYMENT_RESULT = "billing.payment.result";
    public static final String SETTLEMENT_REQUESTED = "billing.settlement.requested";
    public static final String SETTLEMENT_RESULT = "billing.settlement.result";
    public static final String ORCHESTRATION_FAILED = "billing.orchestration.failed";
    public static final String DEAD_LETTER = "billing.dlq";

    private KafkaTopics() {
    }
}
