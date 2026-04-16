package com.banco.co.notification.email.exception;

import com.banco.co.exception.BankingException;
import org.springframework.http.HttpStatus;

public abstract class NotificationException extends BankingException {

    protected NotificationException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    protected NotificationException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}
