package com.mustafabulu.billing.common.tenant;

import java.util.Optional;

public final class TenantContextHolder {
    private static final ThreadLocal<String> TENANT_CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(String tenantId) {
        TENANT_CONTEXT.set(tenantId);
    }

    public static Optional<String> getTenantId() {
        return Optional.ofNullable(TENANT_CONTEXT.get());
    }

    public static void clear() {
        TENANT_CONTEXT.remove();
    }
}
