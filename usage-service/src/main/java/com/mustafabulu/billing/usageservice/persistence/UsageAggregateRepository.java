package com.mustafabulu.billing.usageservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsageAggregateRepository extends MongoRepository<UsageAggregateDocument, String> {
    Optional<UsageAggregateDocument> findByTenantIdAndCustomerIdAndMetricCode(String tenantId,
                                                                               String customerId,
                                                                               String metricCode);
}
