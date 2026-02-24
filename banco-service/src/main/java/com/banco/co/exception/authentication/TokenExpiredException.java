package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends AuthenticationException {

    private static final String ERROR_CODE = "TOKEN_EXPIRED";
    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;

    public TokenExpiredException(String message) {
        super(message, ERROR_CODE, STATUS);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, ERROR_CODE, STATUS, cause);
    }
}
