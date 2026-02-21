package com.mustafabulu.billing.tenantservice.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record Tenant(
        @Schema(description = "Tenant id", example = "67b8d0902c24f41f8f995900")
        String id,
        @Schema(description = "Unique tenant code", example = "acme-tr")
        String tenantCode,
        @Schema(description = "Tenant display name", example = "Acme Turkey")
        String displayName,
        @Schema(description = "Creation timestamp in UTC", example = "2026-02-21T16:30:00Z")
        Instant createdAt
) {
}
