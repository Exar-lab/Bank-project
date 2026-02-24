package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends AuthenticationException {

    private static final String ERROR_CODE = "INVALID_TOKEN";
    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;

    public InvalidTokenException(String message) {
        super(message, ERROR_CODE, STATUS);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, ERROR_CODE, STATUS, cause);
    }
}
