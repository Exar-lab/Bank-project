package com.banco.co.transaction.exception.transaction;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class TransactionException extends BankingException {

    public TransactionException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    public TransactionException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
