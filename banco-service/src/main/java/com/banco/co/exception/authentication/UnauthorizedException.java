package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AuthenticationException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

    public UnauthorizedException() {
        super("Unauthorized access", ERROR_CODE, STATUS);
    }

    public UnauthorizedException(String message) {
        super(message, ERROR_CODE, STATUS);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, ERROR_CODE, STATUS, cause);
    }
}
