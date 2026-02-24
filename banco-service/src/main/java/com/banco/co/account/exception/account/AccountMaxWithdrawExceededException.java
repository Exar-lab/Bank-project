package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class AccountMaxWithdrawExceededException extends AccountException{

    private static final String ERROR_CODE = "Withdraw Exceeded";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public AccountMaxWithdrawExceededException(String accountCode, BigDecimal amount, BigDecimal maxWithdraw) {
        super(
                String.format("Withdraw Exceeded %s. Amount: %s, Max Withdraw: %s",
                        accountCode, amount, maxWithdraw),
                ERROR_CODE,
                STATUS);
        this.addMetadata("accountId", accountCode);
        this.addMetadata("requestedAmount", amount);
        this.addMetadata("availableBalance", maxWithdraw);
    }
    public AccountMaxWithdrawExceededException(String message) {
        super(message, ERROR_CODE, STATUS);
    }
}
