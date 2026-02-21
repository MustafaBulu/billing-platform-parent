package com.mustafabulu.billing.common.idempotency;

import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import java.util.Optional;

public final class IdempotencyKeyResolver {

    private IdempotencyKeyResolver() {
    }

    public static Optional<String> resolveCompositeKey(String operationCode) {
        Optional<String> tenantId = TenantContextHolder.getTenantId();
        Optional<String> idempotencyKey = IdempotencyContextHolder.getIdempotencyKey();

        if (tenantId.isEmpty() || idempotencyKey.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(tenantId.get() + ":" + operationCode + ":" + idempotencyKey.get());
    }
}
