package com.banco.co.exception.authentication;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends AuthenticationException {

    private static final String ERROR_CODE = "ACCOUNT_LOCKED";
    private static final HttpStatus STATUS = HttpStatus.LOCKED;

    public AccountLockedException(String email) {
        super(
                String.format("Account locked for user: %s", email),
                ERROR_CODE,
                STATUS);
        this.addMetadata("email", email);
    }

    public AccountLockedException(String email, int lockDurationMinutes) {
        super(
                String.format("Account locked for user %s. Try again in %d minutes", email, lockDurationMinutes),
                ERROR_CODE,
                STATUS);
        this.addMetadata("email", email);
        this.addMetadata("lockDurationMinutes", lockDurationMinutes);
    }
}
