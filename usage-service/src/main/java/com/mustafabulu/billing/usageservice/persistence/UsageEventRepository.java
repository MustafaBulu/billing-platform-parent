package com.mustafabulu.billing.usageservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsageEventRepository extends MongoRepository<UsageEventDocument, String> {
    Optional<UsageEventDocument> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
}
