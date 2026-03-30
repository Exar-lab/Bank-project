package com.banco.co.exception.fraud;

import org.springframework.http.HttpStatus;

public class FraudBlockedException extends FraudException {

    private static final String ERROR_CODE = "FRAUD_BLOCKED";
    private static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    public FraudBlockedException(String transactionCode, String reason) {
        super(
                "Transaction %s was blocked by fraud detection: %s".formatted(transactionCode, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("transactionCode", transactionCode);
        this.addMetadata("reason", reason);
    }
}
