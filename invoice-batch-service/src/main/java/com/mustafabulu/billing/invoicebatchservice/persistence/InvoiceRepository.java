package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceRepository extends MongoRepository<InvoiceDocument, String> {
    Optional<InvoiceDocument> findByTenantIdAndOperationCodeAndIdempotencyKey(String tenantId,
                                                                               String operationCode,
                                                                               String idempotencyKey);

    Optional<InvoiceDocument> findByInvoiceId(String invoiceId);
}
