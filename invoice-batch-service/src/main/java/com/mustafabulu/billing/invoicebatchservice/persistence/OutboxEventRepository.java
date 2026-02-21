package com.mustafabulu.billing.invoicebatchservice.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OutboxEventRepository extends MongoRepository<OutboxEventDocument, String> {
}
