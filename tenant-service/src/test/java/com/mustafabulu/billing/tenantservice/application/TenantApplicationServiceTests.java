package com.mustafabulu.billing.tenantservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mustafabulu.billing.tenantservice.api.dto.CreateTenantRequest;
import com.mustafabulu.billing.tenantservice.domain.Tenant;
import com.mustafabulu.billing.tenantservice.persistence.TenantDocument;
import com.mustafabulu.billing.tenantservice.persistence.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

class TenantApplicationServiceTests {

    private final TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
    private final TenantApplicationService tenantApplicationService = new TenantApplicationService(tenantRepository);

    @Test
    void shouldCreateTenantWithGeneratedCode() {
        CreateTenantRequest request = new CreateTenantRequest("Tenant A");
        when(tenantRepository.save(any(TenantDocument.class))).thenAnswer(i -> {
            TenantDocument saved = i.getArgument(0);
            saved.setId("id-1");
            return saved;
        });

        Tenant tenant = tenantApplicationService.create(request);

        assertThat(tenant.id()).isEqualTo("id-1");
        assertThat(tenant.tenantCode()).isEqualTo("tenant-a");
        assertThat(tenant.displayName()).isEqualTo("Tenant A");
    }

    @Test
    void shouldGenerateNextCodeWhenBaseCodeExists() {
        CreateTenantRequest request = new CreateTenantRequest("Tenant Dup");
        when(tenantRepository.save(any(TenantDocument.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenAnswer(i -> {
                    TenantDocument saved = i.getArgument(0);
                    saved.setId("id-4");
                    return saved;
                });

        Tenant tenant = tenantApplicationService.create(request);

        assertThat(tenant.id()).isEqualTo("id-4");
        assertThat(tenant.tenantCode()).isEqualTo("tenant-dup-2");
    }

    @Test
    void shouldRetryWhenDuplicateKeyOccursDuringSave() {
        CreateTenantRequest request = new CreateTenantRequest("Tenant C");
        when(tenantRepository.save(any(TenantDocument.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenAnswer(i -> {
                    TenantDocument saved = i.getArgument(0);
                    saved.setId("id-3");
                    return saved;
                });

        Tenant tenant = tenantApplicationService.create(request);

        assertThat(tenant.id()).isEqualTo("id-3");
        assertThat(tenant.tenantCode()).isEqualTo("tenant-c-2");
    }
}
