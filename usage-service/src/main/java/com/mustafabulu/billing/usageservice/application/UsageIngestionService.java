package com.mustafabulu.billing.usageservice.application;

import com.mustafabulu.billing.usageservice.api.dto.UsageEventRequest;
import com.mustafabulu.billing.usageservice.domain.UsageEvent;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Service;

@Service
public class UsageIngestionService {
    private final Map<String, UsageEvent> dedupStore = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> aggregates = new ConcurrentHashMap<>();

    public UsageEvent ingest(UsageEventRequest request) {
        Instant eventTime = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        UsageEvent candidate = new UsageEvent(
                request.tenantId(),
                request.customerId(),
                request.idempotencyKey(),
                request.metricCode(),
                request.quantity(),
                eventTime
        );

        UsageEvent persisted = dedupStore.putIfAbsent(request.idempotencyKey(), candidate);
        if (persisted != null) {
            return persisted;
        }

        String aggregateKey = request.tenantId() + ":" + request.customerId() + ":" + request.metricCode();
        aggregates.computeIfAbsent(aggregateKey, ignored -> new LongAdder()).add(request.quantity());
        return candidate;
    }

    public long currentTotal(String tenantId, String customerId, String metricCode) {
        String key = tenantId + ":" + customerId + ":" + metricCode;
        LongAdder counter = aggregates.get(key);
        return counter == null ? 0L : counter.sum();
    }
}
