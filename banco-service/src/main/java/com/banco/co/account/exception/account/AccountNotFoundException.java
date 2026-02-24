package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends AccountException {

    private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    public AccountNotFoundException(String accountIdentifier) {
        super(
                String.format("Account not found with identifier: %s", accountIdentifier),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountIdentifier", accountIdentifier);
    }

    public AccountNotFoundException(String accountIdentifier, Throwable cause) {
        super(
                String.format("Account not found with identifier: %s", accountIdentifier),
                ERROR_CODE,
                STATUS,
                cause);
        this.addMetadata("accountIdentifier", accountIdentifier);
    }
}
