package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class EnvelopeInsufficientFundsException extends EnvelopeException {

    private static final String ERROR_CODE = "INSUFFICIENT_FUNDS";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public EnvelopeInsufficientFundsException(BigDecimal balance, BigDecimal amount,String envelopeCode) {
        super(
                String.format("Insufficient balance in envelope: %s ",envelopeCode),ERROR_CODE,STATUS
        );
        addMetadata("Balance",balance);
        addMetadata("Requested",amount);
    }

    public EnvelopeInsufficientFundsException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
