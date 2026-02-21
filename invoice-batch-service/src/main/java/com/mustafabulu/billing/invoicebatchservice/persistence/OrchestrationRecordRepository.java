package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrchestrationRecordRepository extends MongoRepository<OrchestrationRecordDocument, String> {
    Optional<OrchestrationRecordDocument> findByTenantIdAndOperationCodeAndIdempotencyKey(String tenantId,
                                                                                           String operationCode,
                                                                                           String idempotencyKey);
}
