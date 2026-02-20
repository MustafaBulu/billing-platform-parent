package com.mustafabulu.billing.usageservice.application;

import com.mustafabulu.billing.usageservice.api.dto.UsageEventRequest;
import com.mustafabulu.billing.usageservice.domain.UsageEvent;
import com.mustafabulu.billing.usageservice.persistence.UsageAggregateDocument;
import com.mustafabulu.billing.usageservice.persistence.UsageAggregateRepository;
import com.mustafabulu.billing.usageservice.persistence.UsageEventDocument;
import com.mustafabulu.billing.usageservice.persistence.UsageEventRepository;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class UsageIngestionService {
    private final UsageEventRepository usageEventRepository;
    private final UsageAggregateRepository usageAggregateRepository;
    private final MongoTemplate mongoTemplate;

    public UsageIngestionService(UsageEventRepository usageEventRepository,
                                 UsageAggregateRepository usageAggregateRepository,
                                 MongoTemplate mongoTemplate) {
        this.usageEventRepository = usageEventRepository;
        this.usageAggregateRepository = usageAggregateRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public UsageEvent ingest(UsageEventRequest request) {
        UsageEventDocument existing = usageEventRepository.findByTenantIdAndIdempotencyKey(
                        request.tenantId(), request.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            return toDomain(existing);
        }

        Instant eventTime = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        UsageEventDocument document = new UsageEventDocument();
        document.setTenantId(request.tenantId());
        document.setCustomerId(request.customerId());
        document.setIdempotencyKey(request.idempotencyKey());
        document.setMetricCode(request.metricCode());
        document.setQuantity(request.quantity());
        document.setOccurredAt(eventTime);
        document.setReceivedAt(Instant.now());

        UsageEventDocument persisted;
        try {
            persisted = usageEventRepository.save(document);
            incrementAggregate(request);
        } catch (DuplicateKeyException duplicateKeyException) {
            persisted = usageEventRepository.findByTenantIdAndIdempotencyKey(request.tenantId(), request.idempotencyKey())
                    .orElseThrow(() -> duplicateKeyException);
        }

        return toDomain(persisted);
    }

    public long currentTotal(String tenantId, String customerId, String metricCode) {
        return usageAggregateRepository.findByTenantIdAndCustomerIdAndMetricCode(tenantId, customerId, metricCode)
                .map(UsageAggregateDocument::getTotalQuantity)
                .orElse(0L);
    }

    private void incrementAggregate(UsageEventRequest request) {
        Query query = Query.query(Criteria.where("tenantId").is(request.tenantId())
                .and("customerId").is(request.customerId())
                .and("metricCode").is(request.metricCode()));

        Update update = new Update()
                .setOnInsert("tenantId", request.tenantId())
                .setOnInsert("customerId", request.customerId())
                .setOnInsert("metricCode", request.metricCode())
                .inc("totalQuantity", request.quantity())
                .set("updatedAt", Instant.now());

        mongoTemplate.upsert(query, update, UsageAggregateDocument.class);
    }

    private UsageEvent toDomain(UsageEventDocument document) {
        return new UsageEvent(
                document.getTenantId(),
                document.getCustomerId(),
                document.getIdempotencyKey(),
                document.getMetricCode(),
                document.getQuantity(),
                document.getOccurredAt()
        );
    }
}
