package com.banco.co.exception.support;

import com.banco.co.exception.ErrorResponseDto;
import com.banco.co.exception.catalog.ErrorCodeCatalog;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class ErrorResponseFactory {

    public ErrorResponseDto businessRuleViolation(String message, Map<String, Object> details) {
        return build(ErrorCodeCatalog.BUSINESS_RULE_VIOLATION, message, details);
    }

    public ErrorResponseDto validationFailed(String message, Map<String, Object> details) {
        return build(ErrorCodeCatalog.VALIDATION_FAILED, message, details);
    }

    public ErrorResponseDto unauthorized(String message, Map<String, Object> details) {
        return build(ErrorCodeCatalog.UNAUTHORIZED, message, details);
    }

    public ErrorResponseDto forbidden(String message, Map<String, Object> details) {
        return build(ErrorCodeCatalog.FORBIDDEN, message, details);
    }

    public ErrorResponseDto internalError(String message, Map<String, Object> details) {
        return build(ErrorCodeCatalog.INTERNAL_ERROR, message, details);
    }

    public ErrorResponseDto withCode(String code, String message, Map<String, Object> details) {
        return build(code, message, details);
    }

    private ErrorResponseDto build(String code, String message, Map<String, Object> details) {
        Map<String, Object> safeDetails = details == null ? Map.of() : details;
        return new ErrorResponseDto(code, message, safeDetails, LocalDateTime.now());
    }
}
