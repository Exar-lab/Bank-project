package com.banco.co.user.exception.user;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public class UserException extends BankingException {
    protected UserException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected UserException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
