package com.banco.co.Transaction.exception.transaction;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class TransactionInvalidAmountException extends TransactionException {

    private static final String ERROR_CODE = "INVALID_AMOUNT";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public TransactionInvalidAmountException(BigDecimal amount) {
        super(
                String.format("Invalid transaction amount: %s", amount),
                ERROR_CODE,
                STATUS);
        this.addMetadata("amount", amount);
    }

    public TransactionInvalidAmountException(String message) {
        super(message, ERROR_CODE, STATUS);
    }

    public TransactionInvalidAmountException(BigDecimal amount, String reason) {
        super(
                String.format("Invalid amount %s: %s", amount, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("amount", amount);
        this.addMetadata("reason", reason);
    }
}
