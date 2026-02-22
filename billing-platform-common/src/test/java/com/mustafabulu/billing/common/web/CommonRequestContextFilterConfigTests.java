package com.mustafabulu.billing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyFilter;
import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import org.junit.jupiter.api.Test;

class CommonRequestContextFilterConfigTests {

    @Test
    void shouldCreateContextFilters() {
        CommonRequestContextFilterConfig config = new CommonRequestContextFilterConfig();

        TenantContextFilter tenantContextFilter = config.tenantContextFilter();
        IdempotencyKeyFilter idempotencyKeyFilter = config.idempotencyKeyFilter();

        assertThat(tenantContextFilter).isNotNull();
        assertThat(idempotencyKeyFilter).isNotNull();
    }
}
