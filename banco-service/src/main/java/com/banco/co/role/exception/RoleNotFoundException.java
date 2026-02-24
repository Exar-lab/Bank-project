package com.banco.co.role.exception;

import org.springframework.http.HttpStatus;

public class RoleNotFoundException extends RoleException{
    private static final String ERROR_CODE = "ROLE_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
    public RoleNotFoundException(String name) {
        super(
                String.format("Role: %s not found",name),ERROR_CODE,STATUS
        );
    }

    protected RoleNotFoundException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
