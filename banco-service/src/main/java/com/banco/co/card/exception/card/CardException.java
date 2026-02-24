package com.banco.co.card.exception.card;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class CardException extends BankingException {

    protected CardException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected CardException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
