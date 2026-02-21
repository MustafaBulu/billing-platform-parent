package com.mustafabulu.billing.settlementservice.application;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyResolver;
import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import com.mustafabulu.billing.settlementservice.persistence.SettlementSagaDocument;
import com.mustafabulu.billing.settlementservice.persistence.SettlementSagaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class SettlementSagaService {

    private static final String OPERATION_CODE = "SETTLEMENT_START";

    private final SettlementSagaRepository settlementSagaRepository;

    public SettlementSagaService(SettlementSagaRepository settlementSagaRepository) {
        this.settlementSagaRepository = settlementSagaRepository;
    }

    public SettlementSaga start(StartSettlementRequest request) {
        String effectiveTenantId = TenantContextHolder.getTenantId().orElse(request.tenantId());
        String effectiveKey = IdempotencyKeyResolver.resolveCompositeKey(OPERATION_CODE)
                .orElse(effectiveTenantId + ":" + OPERATION_CODE + ":" + request.idempotencyKey());

        SettlementSagaDocument existing = settlementSagaRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                effectiveTenantId, OPERATION_CODE, effectiveKey).orElse(null);
        if (existing != null) {
            return toDomain(existing);
        }

        String sagaId = "SAGA-" + UUID.randomUUID();
        List<SettlementStatus> transitions = new ArrayList<>();
        transitions.add(SettlementStatus.STARTED);

        SettlementStatus finalStatus;
        if ("SUCCESS".equalsIgnoreCase(request.paymentStatus())) {
            transitions.add(SettlementStatus.PAYMENT_CONFIRMED);
            transitions.add(SettlementStatus.SETTLED);
            finalStatus = SettlementStatus.SETTLED;
        } else {
            transitions.add(SettlementStatus.FAILED);
            finalStatus = SettlementStatus.FAILED;
        }

        SettlementSagaDocument document = new SettlementSagaDocument();
        document.setSagaId(sagaId);
        document.setTenantId(effectiveTenantId);
        document.setOperationCode(OPERATION_CODE);
        document.setIdempotencyKey(effectiveKey);
        document.setInvoiceId(request.invoiceId());
        document.setPaymentTransactionId(request.paymentTransactionId());
        document.setAmount(request.amount());
        document.setCurrency(request.currency());
        document.setStatus(finalStatus);
        document.setTransitions(List.copyOf(transitions));
        document.setUpdatedAt(Instant.now());

        try {
            return toDomain(settlementSagaRepository.save(document));
        } catch (DuplicateKeyException duplicateKeyException) {
            return settlementSagaRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                            effectiveTenantId, OPERATION_CODE, effectiveKey)
                    .map(this::toDomain)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    public SettlementSaga getById(String sagaId) {
        return settlementSagaRepository.findBySagaId(sagaId)
                .map(this::toDomain)
                .orElse(null);
    }

    private SettlementSaga toDomain(SettlementSagaDocument document) {
        return new SettlementSaga(
                document.getSagaId(),
                document.getTenantId(),
                document.getInvoiceId(),
                document.getPaymentTransactionId(),
                document.getAmount(),
                document.getCurrency(),
                document.getStatus(),
                document.getTransitions(),
                document.getUpdatedAt()
        );
    }
}
