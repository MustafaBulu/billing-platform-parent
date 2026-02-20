package com.mustafabulu.billing.tenantservice.domain;

import java.time.Instant;

public record Tenant(String id, String tenantCode, String displayName, Instant createdAt) {
}
