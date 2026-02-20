package com.mustafabulu.billing.settlementservice.application;

import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import com.mustafabulu.billing.settlementservice.domain.SettlementStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SettlementSagaService {

    private final Map<String, SettlementSaga> sagas = new ConcurrentHashMap<>();

    public SettlementSaga start(StartSettlementRequest request) {
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

        SettlementSaga saga = new SettlementSaga(
                sagaId,
                request.tenantId(),
                request.invoiceId(),
                request.paymentTransactionId(),
                request.amount(),
                request.currency(),
                finalStatus,
                List.copyOf(transitions),
                Instant.now()
        );
        sagas.put(sagaId, saga);
        return saga;
    }

    public SettlementSaga getById(String sagaId) {
        return sagas.get(sagaId);
    }
}
