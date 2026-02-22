package com.mustafabulu.billing.paymentservice.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "payments")
@CompoundIndex(name = "tenant_operation_idempotency_unique", def = "{'tenantId': 1, 'operationCode': 1, 'idempotencyKey': 1}", unique = true)
@SuppressWarnings("unused")
@Getter
@Setter
public class PaymentRecordDocument {
    @Id
    private String id;
    private String tenantId;
    private String operationCode;
    private String idempotencyKey;
    private String transactionId;
    private String invoiceId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String providerReference;
    private Instant processedAt;
}
