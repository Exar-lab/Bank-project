package com.banco.co.exception.validation;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class ValidationException extends BankingException {

    protected ValidationException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected ValidationException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
