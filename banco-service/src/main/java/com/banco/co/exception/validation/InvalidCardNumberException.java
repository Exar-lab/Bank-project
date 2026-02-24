package com.banco.co.exception.validation;

import org.springframework.http.HttpStatus;

public class InvalidCardNumberException extends ValidationException {

    private static final String ERROR_CODE = "INVALID_CARD_NUMBER";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public InvalidCardNumberException(String cardNumber) {
        super(
                String.format("Invalid card number format: %s", cardNumber),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardNumber", cardNumber);
    }

    public InvalidCardNumberException(String cardNumber, String reason) {
        super(
                String.format("Invalid card number %s: %s", cardNumber, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardNumber", cardNumber);
        this.addMetadata("reason", reason);
    }
}
