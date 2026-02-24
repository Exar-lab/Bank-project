package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class InvalidPrincipalException extends AuthenticationException {

    private static final String ERROR_CODE = "INVALID_PRINCIPAL";
    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;

    public InvalidPrincipalException() {
        super("Authentication principal is null or invalid", ERROR_CODE, STATUS);
    }

    public InvalidPrincipalException(String detail) {
        super(
                String.format("Authentication principal is invalid: %s", detail),
                ERROR_CODE,
                STATUS);
    }
}
