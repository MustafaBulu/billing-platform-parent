package com.mustafabulu.billing.common.idempotency;

import java.util.Optional;

public final class IdempotencyContextHolder {
    private static final ThreadLocal<String> IDEMPOTENCY_CONTEXT = new ThreadLocal<>();

    private IdempotencyContextHolder() {
    }

    public static void setIdempotencyKey(String idempotencyKey) {
        IDEMPOTENCY_CONTEXT.set(idempotencyKey);
    }

    public static Optional<String> getIdempotencyKey() {
        return Optional.ofNullable(IDEMPOTENCY_CONTEXT.get());
    }

    public static void clear() {
        IDEMPOTENCY_CONTEXT.remove();
    }
}
