package com.banco.co.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Canonical error response body returned by GlobalExceptionHandler for all error scenarios.
 *
 * <pre>
 * {
 *   "errorCode":  "TRANSACTION_NOT_FOUND",
 *   "message":    "Transaction not found with id: abc-123",
 *   "details":    { "transactionId": "abc-123" },
 *   "timestamp":  "2026-03-27T10:00:00"
 * }
 * </pre>
 */
public record ErrorResponseDto(
        String errorCode,
        String message,
        Map<String, Object> details,
        LocalDateTime timestamp
) {}
