package com.mustafabulu.billing.invoicebatchservice.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import java.util.Collection;

public interface OutboxEventRepository extends MongoRepository<OutboxEventDocument, String> {
    List<OutboxEventDocument> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    List<OutboxEventDocument> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses, Pageable pageable);
}
