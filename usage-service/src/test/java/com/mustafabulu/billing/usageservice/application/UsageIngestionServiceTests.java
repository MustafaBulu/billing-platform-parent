package com.mustafabulu.billing.usageservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.usageservice.api.dto.UsageEventRequest;
import com.mustafabulu.billing.usageservice.domain.UsageEvent;
import com.mustafabulu.billing.usageservice.persistence.UsageAggregateDocument;
import com.mustafabulu.billing.usageservice.persistence.UsageAggregateRepository;
import com.mustafabulu.billing.usageservice.persistence.UsageEventDocument;
import com.mustafabulu.billing.usageservice.persistence.UsageEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

class UsageIngestionServiceTests {

    private final UsageEventRepository usageEventRepository = Mockito.mock(UsageEventRepository.class);
    private final UsageAggregateRepository usageAggregateRepository = Mockito.mock(UsageAggregateRepository.class);
    private final MongoTemplate mongoTemplate = Mockito.mock(MongoTemplate.class);
    private final UsageIngestionService usageIngestionService = new UsageIngestionService(
            usageEventRepository, usageAggregateRepository, mongoTemplate);

    @Test
    void shouldReturnExistingEventWithoutWritingAgain() {
        UsageEventRequest request = request("tenant-1", "customer-1", "idem-1", "api_call", 3L);
        UsageEventDocument existing = document(request, Instant.parse("2026-02-21T00:00:00Z"));
        when(usageEventRepository.findByTenantIdAndIdempotencyKey("tenant-1", "idem-1")).thenReturn(Optional.of(existing));

        UsageEvent event = usageIngestionService.ingest(request);

        assertThat(event.idempotencyKey()).isEqualTo("idem-1");
        verify(usageEventRepository, never()).save(any(UsageEventDocument.class));
        verify(mongoTemplate, never()).upsert(any(), any(), any(Class.class));
    }

    @Test
    void shouldPersistAndIncrementAggregateForNewEvent() {
        UsageEventRequest request = request("tenant-1", "customer-1", "idem-2", "storage_gb", 8L);
        when(usageEventRepository.findByTenantIdAndIdempotencyKey("tenant-1", "idem-2")).thenReturn(Optional.empty());
        when(usageEventRepository.save(any(UsageEventDocument.class))).thenAnswer(i -> i.getArgument(0));

        UsageEvent event = usageIngestionService.ingest(request);

        assertThat(event.quantity()).isEqualTo(8L);
        verify(mongoTemplate).upsert(any(), any(), any(Class.class));
    }

    @Test
    void shouldFallbackToExistingWhenSaveHitsDuplicateAndSkipAggregateUpdate() {
        UsageEventRequest request = request("tenant-1", "customer-1", "idem-3", "api_call", 5L);
        UsageEventDocument existing = document(request, Instant.parse("2026-02-21T00:00:00Z"));
        when(usageEventRepository.findByTenantIdAndIdempotencyKey("tenant-1", "idem-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(usageEventRepository.save(any(UsageEventDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        UsageEvent event = usageIngestionService.ingest(request);

        assertThat(event.idempotencyKey()).isEqualTo("idem-3");
        verify(mongoTemplate, never()).upsert(any(), any(), any(Class.class));
    }

    @Test
    void shouldReturnZeroWhenAggregateMissing() {
        when(usageAggregateRepository.findByTenantIdAndCustomerIdAndMetricCode("tenant-2", "customer-2", "sms"))
                .thenReturn(Optional.empty());

        long total = usageIngestionService.currentTotal("tenant-2", "customer-2", "sms");

        assertThat(total).isZero();
    }

    @Test
    void shouldReturnCurrentTotalWhenAggregateExists() {
        UsageAggregateDocument aggregate = new UsageAggregateDocument();
        aggregate.setTotalQuantity(99L);
        when(usageAggregateRepository.findByTenantIdAndCustomerIdAndMetricCode("tenant-2", "customer-2", "sms"))
                .thenReturn(Optional.of(aggregate));

        long total = usageIngestionService.currentTotal("tenant-2", "customer-2", "sms");

        assertThat(total).isEqualTo(99L);
    }

    private static UsageEventRequest request(String tenantId, String customerId, String idem, String metric, long quantity) {
        return new UsageEventRequest(tenantId, customerId, idem, metric, quantity, Instant.parse("2026-02-21T00:00:00Z"));
    }

    private static UsageEventDocument document(UsageEventRequest request, Instant receivedAt) {
        UsageEventDocument document = new UsageEventDocument();
        document.setTenantId(request.tenantId());
        document.setCustomerId(request.customerId());
        document.setIdempotencyKey(request.idempotencyKey());
        document.setMetricCode(request.metricCode());
        document.setQuantity(request.quantity());
        document.setOccurredAt(request.occurredAt());
        document.setReceivedAt(receivedAt);
        return document;
    }
}
