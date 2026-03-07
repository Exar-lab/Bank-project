package com.banco.co.transaction.exception.transaction;

import org.springframework.http.HttpStatus;

public class TransactionAlreadyProcessedException extends TransactionException {

    private static final String ERROR_CODE = "TRANSACTION_ALREADY_PROCESSED";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public TransactionAlreadyProcessedException(String transactionId) {
        super(
                String.format("Transaction %s has already been processed", transactionId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("transactionId", transactionId);
    }

    public TransactionAlreadyProcessedException(String transactionId, String processedAt) {
        super(
                String.format("Transaction %s was already processed at %s", transactionId, processedAt),
                ERROR_CODE,
                STATUS);
        this.addMetadata("transactionId", transactionId);
        this.addMetadata("processedAt", processedAt);
    }
}
