package com.mustafabulu.billing.settlementservice.application;

import com.mustafabulu.billing.settlementservice.api.dto.StartSettlementRequest;
import com.mustafabulu.billing.settlementservice.domain.SettlementSaga;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SettlementSagaService {

    private final Map<String, SettlementSaga> sagas = new ConcurrentHashMap<>();

    public SettlementSaga start(StartSettlementRequest request) {
        String sagaId = "SAGA-" + UUID.randomUUID();

        SettlementSaga started = new SettlementSaga(
                sagaId,
                request.tenantId(),
                request.invoiceId(),
                request.paymentTransactionId(),
                request.amount(),
                request.currency(),
                "STARTED",
                Instant.now()
        );
        sagas.put(sagaId, started);

        SettlementSaga completed = new SettlementSaga(
                sagaId,
                request.tenantId(),
                request.invoiceId(),
                request.paymentTransactionId(),
                request.amount(),
                request.currency(),
                "COMPLETED",
                Instant.now()
        );
        sagas.put(sagaId, completed);

        return completed;
    }

    public SettlementSaga getById(String sagaId) {
        return sagas.get(sagaId);
    }
}
