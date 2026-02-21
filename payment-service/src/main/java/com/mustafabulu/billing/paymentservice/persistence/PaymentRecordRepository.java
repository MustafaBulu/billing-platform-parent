package com.mustafabulu.billing.paymentservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRecordRepository extends MongoRepository<PaymentRecordDocument, String> {
    Optional<PaymentRecordDocument> findByTenantIdAndOperationCodeAndIdempotencyKey(String tenantId,
                                                                                      String operationCode,
                                                                                      String idempotencyKey);
}
