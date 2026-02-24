package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

public class InvalidPinException extends CardException {

    private static final String ERROR_CODE = "INVALID_PIN";
    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;

    public InvalidPinException(String cardId) {
        super(
                String.format("Invalid PIN provided for card %s", cardId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
    }

    public InvalidPinException(String cardId, int remainingAttempts) {
        super(
                String.format("Invalid PIN for card %s. Remaining attempts: %d", cardId, remainingAttempts),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
        this.addMetadata("remainingAttempts", remainingAttempts);
    }
}
