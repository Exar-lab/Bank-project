package com.banco.co.transaction.exception.transaction;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends TransactionException {

    private static final String ERROR_CODE = "TRANSACTION_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    public TransactionNotFoundException(String transactionId) {
        super(
                String.format("Transaction not found with id: %s", transactionId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("transactionId", transactionId);
    }

    public TransactionNotFoundException(String transactionId, Throwable cause) {
        super(
                String.format("Transaction not found with id: %s", transactionId),
                ERROR_CODE,
                STATUS,
                cause);
        this.addMetadata("transactionId", transactionId);
    }
}
