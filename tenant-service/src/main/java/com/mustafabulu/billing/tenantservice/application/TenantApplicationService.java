package com.mustafabulu.billing.tenantservice.application;

import com.mustafabulu.billing.tenantservice.api.dto.CreateTenantRequest;
import com.mustafabulu.billing.tenantservice.domain.Tenant;
import com.mustafabulu.billing.tenantservice.persistence.TenantDocument;
import com.mustafabulu.billing.tenantservice.persistence.TenantRepository;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TenantApplicationService {
    private final TenantRepository tenantRepository;

    public TenantApplicationService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant create(CreateTenantRequest request) {
        return tenantRepository.findByTenantCode(request.tenantCode())
                .map(this::toDomain)
                .orElseGet(() -> saveNewTenant(request));
    }

    private Tenant saveNewTenant(CreateTenantRequest request) {
        TenantDocument document = new TenantDocument();
        document.setTenantCode(request.tenantCode());
        document.setDisplayName(request.displayName());
        document.setCreatedAt(Instant.now());

        try {
            return toDomain(tenantRepository.save(document));
        } catch (DuplicateKeyException duplicateKeyException) {
            return tenantRepository.findByTenantCode(request.tenantCode())
                    .map(this::toDomain)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    private Tenant toDomain(TenantDocument document) {
        return new Tenant(document.getId(), document.getTenantCode(), document.getDisplayName(), document.getCreatedAt());
    }
}
