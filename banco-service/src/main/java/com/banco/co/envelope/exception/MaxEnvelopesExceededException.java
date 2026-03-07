package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

public class MaxEnvelopesExceededException extends EnvelopeException{
    private static final String ERROR_CODE = "MAX_ENVELOPES_EXCEEDED";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public MaxEnvelopesExceededException(String accountIdentifier) {
        super(String.format("Max envelopes exceeded for account with identifier: %s",accountIdentifier), ERROR_CODE, STATUS);
        addMetadata("accountIdentifier", accountIdentifier);
    }

    protected MaxEnvelopesExceededException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
