package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

public class EnvelopeNotFoundException extends EnvelopeException{
    private static final String ERROR_CODE = "ENVELOPE_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
    public EnvelopeNotFoundException(String envelopeIdentifier) {
        super(
                String.format("Envelope not found with identifier: %s", envelopeIdentifier),
                ERROR_CODE,
                STATUS);
        this.addMetadata("envelopeIdentifier", envelopeIdentifier);
    }

    protected EnvelopeNotFoundException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
