package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

public class AccountClosedException extends AccountException {

    private static final String ERROR_CODE = "ACCOUNT_CLOSED";
    private static final HttpStatus STATUS = HttpStatus.GONE;

    public AccountClosedException(String accountId) {
        super(
                String.format("Account %s is closed", accountId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
    }

    public AccountClosedException(String accountId, String closedDate) {
        super(
                String.format("Account %s was closed on %s", accountId, closedDate),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
        this.addMetadata("closedDate", closedDate);
    }
}
