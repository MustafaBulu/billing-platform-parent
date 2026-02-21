package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "orchestration_records")
@CompoundIndex(name = "tenant_operation_idempotency_unique", def = "{'tenantId': 1, 'operationCode': 1, 'idempotencyKey': 1}", unique = true)
@SuppressWarnings("unused")
public class OrchestrationRecordDocument {
    @Id
    private String id;
    private String orchestrationId;
    private String tenantId;
    private String operationCode;
    private String idempotencyKey;
    private String invoiceId;
    private String paymentTransactionId;
    private String settlementSagaId;
    private OrchestrationStatus status;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrchestrationId() { return orchestrationId; }
    public void setOrchestrationId(String orchestrationId) { this.orchestrationId = orchestrationId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(String paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }
    public String getSettlementSagaId() { return settlementSagaId; }
    public void setSettlementSagaId(String settlementSagaId) { this.settlementSagaId = settlementSagaId; }
    public OrchestrationStatus getStatus() { return status; }
    public void setStatus(OrchestrationStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
