package com.banco.co.exception.fraud;

import org.springframework.http.HttpStatus;

public class SuspiciousActivityException extends FraudException {

    private static final String ERROR_CODE = "SUSPICIOUS_ACTIVITY";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

    public SuspiciousActivityException(String accountId, String activityDescription) {
        super(
                String.format("Suspicious activity detected on account %s: %s", accountId, activityDescription),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
        this.addMetadata("activityDescription", activityDescription);
    }

    public SuspiciousActivityException(String message) {
        super(message, ERROR_CODE, STATUS);
    }
}
