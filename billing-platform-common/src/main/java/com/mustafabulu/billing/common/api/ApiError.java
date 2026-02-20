package com.mustafabulu.billing.common.api;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp) {
}
