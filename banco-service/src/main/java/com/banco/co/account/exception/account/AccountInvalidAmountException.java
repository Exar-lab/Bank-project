package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class AccountInvalidAmountException extends AccountException {

    private static final String ERROR_CODE = "INVALID_AMOUNT";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public AccountInvalidAmountException(BigDecimal amount, String reason) {
        super(
                String.format("Invalid amount %s: %s", amount, reason),
                ERROR_CODE,
                STATUS
        );
        this.addMetadata("amount", amount);
        this.addMetadata("reason", reason);
    }
}
