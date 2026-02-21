package com.mustafabulu.billing.settlementservice.persistence;

import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "settlement_sagas")
@CompoundIndex(name = "tenant_operation_idempotency_unique", def = "{'tenantId': 1, 'operationCode': 1, 'idempotencyKey': 1}", unique = true)
@SuppressWarnings("unused")
public class SettlementSagaDocument {
    @Id
    private String id;
    private String sagaId;
    private String tenantId;
    private String operationCode;
    private String idempotencyKey;
    private String invoiceId;
    private String paymentTransactionId;
    private BigDecimal amount;
    private String currency;
    private SettlementStatus status;
    private List<SettlementStatus> transitions;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
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
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }
    public List<SettlementStatus> getTransitions() { return transitions; }
    public void setTransitions(List<SettlementStatus> transitions) { this.transitions = transitions; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
