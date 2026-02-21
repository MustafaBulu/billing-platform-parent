package com.mustafabulu.billing.tenantservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @Schema(description = "Human-readable tenant name", example = "Acme Turkey")
        @NotBlank String displayName
) {
}
