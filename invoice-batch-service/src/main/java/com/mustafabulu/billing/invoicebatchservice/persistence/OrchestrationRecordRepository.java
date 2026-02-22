package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.util.Optional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrchestrationRecordRepository extends MongoRepository<OrchestrationRecordDocument, String> {
    Optional<OrchestrationRecordDocument> findByTenantIdAndOperationCodeAndIdempotencyKey(String tenantId,
                                                                                           String operationCode,
                                                                                           String idempotencyKey);

    List<OrchestrationRecordDocument> findByStatusInAndUpdatedAtBefore(Collection<OrchestrationStatus> statuses,
                                                                       Instant cutoff);
}
