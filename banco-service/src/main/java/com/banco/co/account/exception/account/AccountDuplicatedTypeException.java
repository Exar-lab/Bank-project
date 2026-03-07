package com.banco.co.account.exception.account;

import com.banco.co.account.enums.AccountType;
import org.springframework.http.HttpStatus;


public class AccountDuplicatedTypeException extends AccountException {
    private static final String ERROR_CODE = "DUPLICATED_ACCOUNT_TYPE";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public AccountDuplicatedTypeException(String userEmail, AccountType accountType) {
        super(
                String.format("User %s already has an active %s account",
                        userEmail,
                        accountType != null ? accountType.name().toLowerCase() : "unknown"),
                ERROR_CODE,
                STATUS
        );
        addMetadata("User email", userEmail);
    }

    protected AccountDuplicatedTypeException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
