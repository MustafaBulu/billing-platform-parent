package com.mustafabulu.billing.paymentservice.config;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyFilter;
import com.mustafabulu.billing.common.tenant.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestContextFilterConfig {

    @Bean
    public TenantContextFilter tenantContextFilter() {
        return new TenantContextFilter();
    }

    @Bean
    public IdempotencyKeyFilter idempotencyKeyFilter() {
        return new IdempotencyKeyFilter();
    }
}
