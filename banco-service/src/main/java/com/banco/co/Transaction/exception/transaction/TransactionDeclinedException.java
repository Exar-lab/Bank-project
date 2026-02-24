package com.banco.co.Transaction.exception.transaction;

import org.springframework.http.HttpStatus;

public class TransactionDeclinedException extends TransactionException {

    private static final String ERROR_CODE = "TRANSACTION_DECLINED";
    private static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_CONTENT;

    public TransactionDeclinedException(String transactionId, String reason) {
        super(
                String.format("Transaction %s was declined. Reason: %s", transactionId, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("transactionId", transactionId);
        this.addMetadata("reason", reason);
    }

    public TransactionDeclinedException(String reason) {
        super(
                String.format("Transaction declined: %s", reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("reason", reason);
    }
}
