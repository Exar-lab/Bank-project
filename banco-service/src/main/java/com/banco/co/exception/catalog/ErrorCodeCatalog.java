package com.banco.co.exception.catalog;

/**
 * Centralized API error codes used by MVC and Security boundaries.
 */
public final class ErrorCodeCatalog {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";

    private ErrorCodeCatalog() {
        throw new IllegalStateException("Utility class");
    }
}
