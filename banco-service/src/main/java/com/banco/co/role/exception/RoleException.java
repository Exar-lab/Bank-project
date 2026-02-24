package com.banco.co.role.exception;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public class RoleException extends BankingException {
    protected RoleException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected RoleException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
