package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class EnvelopeInsufficientFundsException extends EnvelopeException {

    private static final String ERRORCODE = "INSUFFICIENT_FUNDS";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public EnvelopeInsufficientFundsException(BigDecimal balance, BigDecimal amount,String envelopeCode) {
        super(
                String.format("Insufficient balance: %s ",envelopeCode),ERRORCODE,STATUS
        );
    }

    public EnvelopeInsufficientFundsException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
