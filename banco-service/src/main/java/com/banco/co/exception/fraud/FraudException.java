package com.banco.co.exception.fraud;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class FraudException extends BankingException {

    protected FraudException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected FraudException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
