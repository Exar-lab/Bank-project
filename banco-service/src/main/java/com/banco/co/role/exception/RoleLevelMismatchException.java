package com.banco.co.role.exception;

import org.springframework.http.HttpStatus;

public class RoleLevelMismatchException extends RoleException {
    private static final String ERROR_CODE = "ROLE_LEVEL_MISMATCH";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;
    public RoleLevelMismatchException(String roleName) {
        super(String.format("Cannot assign role %s. Insufficient privilege level",roleName),
                ERROR_CODE,
                STATUS);
    }

    protected RoleLevelMismatchException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
