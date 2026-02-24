package com.banco.co.user.exception.user;

import org.springframework.http.HttpStatus;

public class UserAlreadyExist extends UserException {
    private static final String ERROR_CODE = "USER_ALREADY_EXIST";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public UserAlreadyExist(String userInformation) {
        super(String.format("User: %s already exists",userInformation ) ,ERROR_CODE, STATUS);
    }

    protected UserAlreadyExist(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
