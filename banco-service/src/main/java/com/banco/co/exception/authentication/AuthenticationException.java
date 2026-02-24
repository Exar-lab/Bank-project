package com.banco.co.exception.authentication;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class AuthenticationException extends BankingException {

    protected AuthenticationException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected AuthenticationException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
