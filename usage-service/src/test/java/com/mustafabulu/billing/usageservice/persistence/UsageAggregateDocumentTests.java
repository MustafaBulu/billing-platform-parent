package com.mustafabulu.billing.usageservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UsageAggregateDocumentTests {

    @Test
    void shouldExposeAllMutableFields() {
        UsageAggregateDocument document = new UsageAggregateDocument();
        Instant updatedAt = Instant.parse("2026-02-22T00:00:00Z");

        document.setId("agg-1");
        document.setTenantId("tenant-1");
        document.setCustomerId("customer-1");
        document.setMetricCode("api_call");
        document.setTotalQuantity(42L);
        document.setUpdatedAt(updatedAt);

        assertThat(document.getId()).isEqualTo("agg-1");
        assertThat(document.getTenantId()).isEqualTo("tenant-1");
        assertThat(document.getCustomerId()).isEqualTo("customer-1");
        assertThat(document.getMetricCode()).isEqualTo("api_call");
        assertThat(document.getTotalQuantity()).isEqualTo(42L);
        assertThat(document.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
