package com.banco.co.user.exception.user;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends UserException {

    private static final String ERROR_CODE = "USER_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
    public UserNotFoundException(String userInformation) {
        super(String.format("Active user: %s not found", userInformation), ERROR_CODE, STATUS);
    }

    protected UserNotFoundException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
