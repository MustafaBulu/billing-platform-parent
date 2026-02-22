package com.mustafabulu.billing.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdempotencyKeyResolverTests {

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        IdempotencyContextHolder.clear();
    }

    @Test
    void shouldResolveCompositeKeyWhenTenantAndIdempotencyPresent() {
        TenantContextHolder.setTenantId("tenant-a");
        IdempotencyContextHolder.setIdempotencyKey("idem-1");

        assertThat(IdempotencyKeyResolver.resolveCompositeKey("PAYMENT"))
                .contains("tenant-a:PAYMENT:idem-1");
    }

    @Test
    void shouldReturnEmptyWhenMissingTenant() {
        IdempotencyContextHolder.setIdempotencyKey("idem-1");

        assertThat(IdempotencyKeyResolver.resolveCompositeKey("PAYMENT")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenMissingIdempotency() {
        TenantContextHolder.setTenantId("tenant-a");

        assertThat(IdempotencyKeyResolver.resolveCompositeKey("PAYMENT")).isEmpty();
    }
}
