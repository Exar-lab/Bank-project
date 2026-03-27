package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class AccountBlockedFundsException extends AccountException {

    private static final String ERROR_CODE = "BLOCKED_FUNDS_VIOLATION";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public AccountBlockedFundsException(String accountCode, BigDecimal requested, BigDecimal blocked, String operation) {
        super(
                String.format("Cannot %s %.2f: only %.2f blocked in account %s",
                        operation, requested, blocked, accountCode),
                ERROR_CODE,
                STATUS
        );
        this.addMetadata("accountCode", accountCode);
        this.addMetadata("requestedAmount", requested);
        this.addMetadata("blockedBalance", blocked);
        this.addMetadata("operation", operation);
    }
}
