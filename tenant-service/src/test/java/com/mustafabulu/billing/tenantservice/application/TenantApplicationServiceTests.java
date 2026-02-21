package com.mustafabulu.billing.tenantservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.tenantservice.api.dto.CreateTenantRequest;
import com.mustafabulu.billing.tenantservice.domain.Tenant;
import com.mustafabulu.billing.tenantservice.persistence.TenantDocument;
import com.mustafabulu.billing.tenantservice.persistence.TenantRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

class TenantApplicationServiceTests {

    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final TenantApplicationService tenantApplicationService = new TenantApplicationService(tenantRepository);

    @Test
    void shouldReturnExistingTenantWhenTenantCodeAlreadyExists() {
        CreateTenantRequest request = new CreateTenantRequest("tenant-a", "Tenant A");
        TenantDocument existing = document("id-1", "tenant-a", "Tenant A");
        when(tenantRepository.findByTenantCode("tenant-a")).thenReturn(Optional.of(existing));

        Tenant tenant = tenantApplicationService.create(request);

        assertThat(tenant.id()).isEqualTo("id-1");
        assertThat(tenant.tenantCode()).isEqualTo("tenant-a");
    }

    @Test
    void shouldCreateTenantWhenNotExists() {
        CreateTenantRequest request = new CreateTenantRequest("tenant-b", "Tenant B");
        when(tenantRepository.findByTenantCode("tenant-b")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(TenantDocument.class))).thenAnswer(i -> {
            TenantDocument saved = i.getArgument(0);
            saved.setId("id-2");
            return saved;
        });

        Tenant tenant = tenantApplicationService.create(request);

        assertThat(tenant.id()).isEqualTo("id-2");
        assertThat(tenant.displayName()).isEqualTo("Tenant B");
    }

    @Test
    void shouldFallbackToExistingTenantOnDuplicateSave() {
        CreateTenantRequest request = new CreateTenantRequest("tenant-c", "Tenant C");
        TenantDocument existing = document("id-3", "tenant-c", "Tenant C");
        when(tenantRepository.findByTenantCode("tenant-c"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(TenantDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        Tenant tenant = tenantApplicationService.create(request);

        assertThat(tenant.id()).isEqualTo("id-3");
        assertThat(tenant.tenantCode()).isEqualTo("tenant-c");
    }

    private static TenantDocument document(String id, String code, String name) {
        TenantDocument document = new TenantDocument();
        document.setId(id);
        document.setTenantCode(code);
        document.setDisplayName(name);
        document.setCreatedAt(Instant.parse("2026-02-21T00:00:00Z"));
        return document;
    }
}
