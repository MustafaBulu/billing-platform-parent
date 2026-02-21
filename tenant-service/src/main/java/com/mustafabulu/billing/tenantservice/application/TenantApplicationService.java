package com.mustafabulu.billing.tenantservice.application;

import com.mustafabulu.billing.tenantservice.api.dto.CreateTenantRequest;
import com.mustafabulu.billing.common.exception.ConflictException;
import com.mustafabulu.billing.tenantservice.domain.Tenant;
import com.mustafabulu.billing.tenantservice.persistence.TenantDocument;
import com.mustafabulu.billing.tenantservice.persistence.TenantRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TenantApplicationService {
    private final TenantRepository tenantRepository;

    public TenantApplicationService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant create(CreateTenantRequest request) {
        String baseCode = toTenantCodeBase(request.displayName());
        for (int attempt = 1; attempt <= 100; attempt++) {
            String candidateCode = attempt == 1 ? baseCode : baseCode + "-" + attempt;
            TenantDocument document = new TenantDocument();
            document.setTenantCode(candidateCode);
            document.setDisplayName(request.displayName());
            document.setCreatedAt(Instant.now());
            try {
                return toDomain(tenantRepository.save(document));
            } catch (DuplicateKeyException ignored) {
                // concurrent insert with same generated code, continue with next suffix
            }
        }
        throw new ConflictException("Unable to generate unique tenant code for: " + request.displayName());
    }

    private String toTenantCodeBase(String displayName) {
        String normalized = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "")
                .replaceAll("-{2,}", "-");
        return normalized.isBlank() ? "tenant" : normalized;
    }

    private Tenant toDomain(TenantDocument document) {
        return new Tenant(document.getId(), document.getTenantCode(), document.getDisplayName(), document.getCreatedAt());
    }
}
