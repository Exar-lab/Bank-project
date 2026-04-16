package com.banco.co.notification.email.exception;

import org.springframework.http.HttpStatus;

public class EmailSerializationException extends NotificationException {

    private static final String ERROR_CODE = "EMAIL_SERIALIZATION_FAILED";

    public EmailSerializationException(String templateName, Throwable cause) {
        super("Failed to render email template " + templateName, ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR, cause);
        addMetadata("templateName", templateName);
    }
}
