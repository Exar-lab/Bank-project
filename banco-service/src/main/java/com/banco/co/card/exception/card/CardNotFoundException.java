package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

public class CardNotFoundException extends CardException {

    private static final String ERROR_CODE = "CARD_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    public CardNotFoundException(String cardIdentifier) {
        super(
                String.format("Card not found with identifier: %s", cardIdentifier),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardIdentifier", cardIdentifier);
    }

    public CardNotFoundException(String cardIdentifier, Throwable cause) {
        super(
                String.format("Card not found with identifier: %s", cardIdentifier),
                ERROR_CODE,
                STATUS,
                cause);
        this.addMetadata("cardIdentifier", cardIdentifier);
    }
}
