package com.banco.co.envelope.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EnvelopeLockedException extends EnvelopeException {

    private static final String ERRORCODE = "ENVELOPE_BLOCKED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;
    public EnvelopeLockedException(String envelopeCode, LocalDate dateTime,String reason) {
        super(
                String.format("Envelope is locked: %s until: %s", envelopeCode,dateTime), ERRORCODE, STATUS
        );
        addMetadata("reason", reason);
    }

    protected EnvelopeLockedException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
