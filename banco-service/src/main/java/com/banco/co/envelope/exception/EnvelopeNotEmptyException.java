package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

public class EnvelopeNotEmptyException extends EnvelopeException {
    private static final String ERROR_CODE = "ENVELOPE_NOT_EMPTY";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    public EnvelopeNotEmptyException(String envelopeCode) {
        super(String.format("Cannot delete envelope: %s with non-zero balance. Please withdraw funds first",envelopeCode), ERROR_CODE, STATUS);
        addMetadata("envelopeCode", envelopeCode);
    }

    protected EnvelopeNotEmptyException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
