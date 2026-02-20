package com.mustafabulu.billing.tenantservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TenantRepository extends MongoRepository<TenantDocument, String> {
    Optional<TenantDocument> findByTenantCode(String tenantCode);
}
