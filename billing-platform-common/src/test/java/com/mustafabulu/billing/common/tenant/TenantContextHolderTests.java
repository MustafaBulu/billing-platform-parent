package com.mustafabulu.billing.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantContextHolderTests {

    @Test
    void shouldStoreAndClearTenantId() {
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId("tenant-a");

        assertThat(TenantContextHolder.getTenantId()).contains("tenant-a");

        TenantContextHolder.clear();
        assertThat(TenantContextHolder.getTenantId()).isEmpty();
    }
}
