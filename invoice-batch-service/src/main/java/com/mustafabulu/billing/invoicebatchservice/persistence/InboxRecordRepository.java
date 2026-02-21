package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InboxRecordRepository extends MongoRepository<InboxRecordDocument, String> {
    Optional<InboxRecordDocument> findByTenantIdAndOperationCodeAndIdempotencyKey(String tenantId,
                                                                                   String operationCode,
                                                                                   String idempotencyKey);
}
