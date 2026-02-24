package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class AccountInsufficientFundsException extends AccountException {

    private static final String ERROR_CODE = "INSUFFICIENT_FUNDS";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public AccountInsufficientFundsException(String accountId, BigDecimal requested, BigDecimal available) {
        super(
                String.format("Insufficient funds in account %s. Requested: %s, Available: %s",
                        accountId, requested, available),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountId);
        this.addMetadata("requestedAmount", requested);
        this.addMetadata("availableBalance", available);
    }

    public AccountInsufficientFundsException(String message) {
        super(message, ERROR_CODE, STATUS);
    }
}
