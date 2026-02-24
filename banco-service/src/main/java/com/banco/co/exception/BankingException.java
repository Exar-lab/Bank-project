package com.banco.co.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class BankingException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> metadata;

    protected BankingException(
            String message,
            String errorCode,
            HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.metadata = new HashMap<>();
    }

    protected BankingException(
            String message,
            String errorCode,
            HttpStatus httpStatus,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.metadata = new HashMap<>();
    }

    public BankingException addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
}
