package com.banco.co.account.exception.account;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class AccountHasActiveEnvelopesException extends AccountException {
    private static final String ERROR_CODE = "ACCOUNT_HAS_ACTIVE_ENVELOPES";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public AccountHasActiveEnvelopesException(String accountCode, BigDecimal balance) {
        super(String.format("Action denied: Account %s still has active envelopes. All envelopes must be deleted before closing the account.",accountCode), ERROR_CODE, STATUS);
        addMetadata("Account code",accountCode);
        addMetadata("balance",balance);
    }

    protected AccountHasActiveEnvelopesException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
