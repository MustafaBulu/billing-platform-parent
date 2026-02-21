package com.mustafabulu.billing.common.web;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyFilter;
import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonRequestContextFilterConfig {

    @Bean
    @ConditionalOnMissingBean
    public TenantContextFilter tenantContextFilter() {
        return new TenantContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyKeyFilter idempotencyKeyFilter() {
        return new IdempotencyKeyFilter();
    }
}
