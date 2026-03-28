package com.banco.co.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
                .body(new ErrorResponseDto(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getMetadata(),
                        LocalDateTime.now()
                ));
    }

    /**
     * Handles @Valid / @Validated failures. Returns 400 with a map of field -> error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (existing, replacement) -> existing
                ));

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        fieldErrors,
                        LocalDateTime.now()
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
                .body(new ErrorResponseDto(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again later.",
                        Map.of(),
                        LocalDateTime.now()
                ));
    }
}
