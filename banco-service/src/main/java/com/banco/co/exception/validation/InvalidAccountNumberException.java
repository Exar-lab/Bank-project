package com.banco.co.exception.validation;

import org.springframework.http.HttpStatus;

public class InvalidAccountNumberException extends ValidationException {

    private static final String ERROR_CODE = "INVALID_ACCOUNT_NUMBER";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public InvalidAccountNumberException(String accountNumber) {
        super(
                String.format("Invalid account number format: %s", accountNumber),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountNumber", accountNumber);
    }

    public InvalidAccountNumberException(String accountNumber, String reason) {
        super(
                String.format("Invalid account number %s: %s", accountNumber, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountNumber", accountNumber);
        this.addMetadata("reason", reason);
    }
}
