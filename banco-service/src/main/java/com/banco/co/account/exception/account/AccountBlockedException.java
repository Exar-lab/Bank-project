package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

public class AccountBlockedException extends AccountException {

    private static final String ERROR_CODE = "ACCOUNT_BLOCKED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

    public AccountBlockedException(String accountId) {
        super(
                String.format("Account %s is blocked", accountId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
    }

    public AccountBlockedException(String accountId, String reason) {
        super(
                String.format("Account %s is blocked. Reason: %s", accountId, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
        this.addMetadata("reason", reason);
    }
}
