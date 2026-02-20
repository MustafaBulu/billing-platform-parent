package com.mustafabulu.billing.tenantservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(@NotBlank String tenantCode, @NotBlank String displayName) {
}
