package com.banco.co.transaction.exception.transaction;

import com.banco.co.transaction.enums.TransactionStatus;
import org.springframework.http.HttpStatus;

public class TransactionStatusException extends TransactionException {
    private static final String ERROR_CODE = "STATUS_ERROR";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public TransactionStatusException(String transactionCode, TransactionStatus current,TransactionStatus requested) {
        super(String.format("Transaction status error with id:  %s", transactionCode), ERROR_CODE, STATUS);
        this.addMetadata("Current status", current);
        this.addMetadata("Requested status", requested);
    }

    public TransactionStatusException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
