package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

public class EnvelopeLockedException extends EnvelopeException {

    private static final String ERRORCODE = "ENVELOPE_BLOCKED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;
    public EnvelopeLockedException(String envelopeCode) {
        super(
                String.format("Envelope is blocked: %s", envelopeCode), ERRORCODE, STATUS
        );
    }

    protected EnvelopeLockedException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
