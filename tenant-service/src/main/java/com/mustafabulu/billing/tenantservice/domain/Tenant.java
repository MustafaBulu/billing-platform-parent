package com.mustafabulu.billing.tenantservice.domain;

import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, String tenantCode, String displayName, Instant createdAt) {
}
