package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AuthenticationException {

    private static final String ERROR_CODE = "INVALID_CREDENTIALS";
    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;

    public InvalidCredentialsException() {
        super("Invalid credentials provided", ERROR_CODE, STATUS);
    }

    public InvalidCredentialsException(String username) {
        super(
                String.format("Invalid credentials for user: %s", username),
                ERROR_CODE,
                STATUS);
        this.addMetadata("username", username);
    }
}
