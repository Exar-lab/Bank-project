package com.banco.co.account.exception.account;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class AccountException extends BankingException {

    protected AccountException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected AccountException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }

}
