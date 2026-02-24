package com.banco.co.envelope.exception;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public class EnvelopeException extends BankingException {
    protected EnvelopeException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected EnvelopeException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
