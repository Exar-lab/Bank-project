package com.banco.co.notification.email.exception;

import org.springframework.http.HttpStatus;

public class EmailDeliveryException extends NotificationException {

    private static final String ERROR_CODE = "EMAIL_DELIVERY_FAILED";

    public EmailDeliveryException(String eventId, Throwable cause) {
        super("Failed to deliver email for event " + eventId, ERROR_CODE, HttpStatus.BAD_GATEWAY, cause);
        addMetadata("eventId", eventId);
    }
}
