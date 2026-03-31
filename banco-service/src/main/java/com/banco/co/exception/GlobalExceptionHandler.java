package com.banco.co.exception;

import com.banco.co.exception.support.ErrorResponseFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DEFAULT_VALIDATION_MESSAGE = "Request validation failed";
    private static final String DEFAULT_INTERNAL_MESSAGE = "An unexpected error occurred. Please try again later.";

    private final ErrorResponseFactory errorResponseFactory;

    public GlobalExceptionHandler(ErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    /**
     * Handles all BankingException subclasses (AccountException, TransactionException,
     * EnvelopeException, CardException, etc.). Each subclass self-encodes its HTTP status,
     * error code, and metadata — so a single handler covers the entire hierarchy.
     */
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponseDto> handleBankingException(BankingException ex) {
        log.error("Banking exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(errorResponseFactory.withCode(ex.getErrorCode(), ex.getMessage(), ex.getMetadata()));
    }

    /**
     * Handles @Valid / @Validated failures. Returns 400 with a map of field -> error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = extractFieldErrors(ex.getBindingResult().getFieldErrors());

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.validationFailed(DEFAULT_VALIDATION_MESSAGE, fieldErrors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        Map<String, Object> errors = extractMessageErrors(ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .map(this::getSafeMessage)
                .toList());

        log.warn("Method validation failed: {}", errors);

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.validationFailed(DEFAULT_VALIDATION_MESSAGE, errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponseDto> handleBindException(BindException ex) {
        Map<String, Object> fieldErrors = extractFieldErrors(ex.getBindingResult().getFieldErrors());

        log.warn("Bind validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.validationFailed(DEFAULT_VALIDATION_MESSAGE, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> errors = extractMessageErrors(ex.getConstraintViolations().stream()
                .map(this::formatConstraintViolation)
                .toList());

        log.warn("Constraint validation failed: {}", errors);

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.validationFailed(DEFAULT_VALIDATION_MESSAGE, errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument provided: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.validationFailed(
                        DEFAULT_VALIDATION_MESSAGE,
                        extractMessageErrors(java.util.List.of(getSafeMessage(ex.getMessage())))
                ));
    }

    /**
     * Catch-all for any unexpected exception. Logs the full stack trace internally
     * but NEVER exposes it to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(errorResponseFactory.internalError(DEFAULT_INTERNAL_MESSAGE, Map.of()));
    }

    private Map<String, Object> extractFieldErrors(java.util.List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> getSafeMessage(error.getDefaultMessage()),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Object> extractMessageErrors(java.util.List<String> messages) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            details.put("error-" + (i + 1), messages.get(i));
        }

        return details;
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "parameter";
        return path + ": " + getSafeMessage(violation.getMessage());
    }

    private String getSafeMessage(String message) {
        return message == null || message.isBlank() ? "invalid" : message;
    }
}
