package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class AccountNotEmptyException extends AccountException {
    private static final String ERROR_CODE = "ACCOUNT_NOT_EMPTY";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public AccountNotEmptyException(String accountCode, BigDecimal balance) {
        super(String.format("Action denied: Account %s still has a remaining balance. It must be zero to proceed.", accountCode),ERROR_CODE,STATUS);
        addMetadata("Account code",accountCode);
        addMetadata("balance",balance);
    }

    protected AccountNotEmptyException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
