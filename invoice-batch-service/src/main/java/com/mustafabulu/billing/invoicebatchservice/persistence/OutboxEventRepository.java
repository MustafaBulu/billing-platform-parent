package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;

public interface OutboxEventRepository extends MongoRepository<OutboxEventDocument, String> {
    List<OutboxEventDocument> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
