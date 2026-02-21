package com.mustafabulu.billing.settlementservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SettlementSagaRepository extends MongoRepository<SettlementSagaDocument, String> {
    Optional<SettlementSagaDocument> findByTenantIdAndOperationCodeAndIdempotencyKey(String tenantId,
                                                                                      String operationCode,
                                                                                      String idempotencyKey);

    Optional<SettlementSagaDocument> findBySagaId(String sagaId);
}
