package com.mustafabulu.billing.common.exception;

public enum ApiErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR"),
    INVALID_JSON("INVALID_JSON"),
    DOMAIN_VALIDATION_ERROR("DOMAIN_VALIDATION_ERROR"),
    CONSTRAINT_VIOLATION("CONSTRAINT_VIOLATION"),
    NOT_FOUND("NOT_FOUND"),
    CONFLICT("CONFLICT"),
    FAILED_DEPENDENCY("FAILED_DEPENDENCY"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    private final String value;

    ApiErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
