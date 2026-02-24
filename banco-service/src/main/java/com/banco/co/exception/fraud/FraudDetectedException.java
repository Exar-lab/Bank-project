package com.banco.co.exception.fraud;

import org.springframework.http.HttpStatus;

public class FraudDetectedException extends FraudException {

    private static final String ERROR_CODE = "FRAUD_DETECTED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

    public FraudDetectedException(String accountId) {
        super(
                String.format("Fraud detected on account %s. Account has been flagged for review", accountId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
    }

    public FraudDetectedException(String accountId, String transactionId) {
        super(
                String.format("Fraud detected on account %s related to transaction %s", accountId, transactionId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
        this.addMetadata("transactionId", transactionId);
    }
}
