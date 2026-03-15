package com.banco.co.account.exception.account;

import com.banco.co.account.enums.AccountStatus;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends AccountException {
    private static final String ERROR_CODE = "ACCOUNT_NOT_ACTIVE";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public AccountNotActiveException(String accountCode, AccountStatus accountStatus) {
        super(
                String.format("Action denied: Account %s is currently %s and must be ACTIVE to proceed.",
                        accountCode,
                        accountStatus),
                ERROR_CODE,
                STATUS
        );
        addMetadata("accountCode", accountCode);
        addMetadata("currentStatus", accountStatus);
    }

    protected AccountNotActiveException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
