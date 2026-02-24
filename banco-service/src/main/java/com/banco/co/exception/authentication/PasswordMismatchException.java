package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class PasswordMismatchException extends AuthenticationException{
    private final static String ERROR_CODE = "PASSWORD_MISMATCH";
    private final static HttpStatus STATUS = HttpStatus.CONFLICT;
    public PasswordMismatchException() {
        super("Passwords do not match",ERROR_CODE,STATUS);
    }

    protected PasswordMismatchException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
