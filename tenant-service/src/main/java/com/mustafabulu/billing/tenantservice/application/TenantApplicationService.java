package com.mustafabulu.billing.tenantservice.application;

import com.mustafabulu.billing.tenantservice.api.dto.CreateTenantRequest;
import com.mustafabulu.billing.tenantservice.domain.Tenant;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TenantApplicationService {
    private final Map<String, Tenant> tenantsByCode = new ConcurrentHashMap<>();

    public Tenant create(CreateTenantRequest request) {
        return tenantsByCode.computeIfAbsent(request.tenantCode(), key ->
                new Tenant(UUID.randomUUID(), request.tenantCode(), request.displayName(), Instant.now())
        );
    }
}
