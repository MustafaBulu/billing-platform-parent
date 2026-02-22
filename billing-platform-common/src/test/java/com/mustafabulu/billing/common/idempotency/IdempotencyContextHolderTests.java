package com.mustafabulu.billing.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdempotencyContextHolderTests {

    @AfterEach
    void clearContext() {
        IdempotencyContextHolder.clear();
    }

    @Test
    void shouldStoreAndClearIdempotencyKey() {
        IdempotencyContextHolder.setIdempotencyKey("idem-1");

        assertThat(IdempotencyContextHolder.getIdempotencyKey()).contains("idem-1");

        IdempotencyContextHolder.clear();
        assertThat(IdempotencyContextHolder.getIdempotencyKey()).isEmpty();
    }
}
