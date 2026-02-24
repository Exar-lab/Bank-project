package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

public class CardExpiredException extends CardException {

    private static final String ERROR_CODE = "CARD_EXPIRED";
    private static final HttpStatus STATUS = HttpStatus.GONE;

    public CardExpiredException(String cardId) {
        super(
                String.format("Card %s has expired", cardId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
    }

    public CardExpiredException(String cardId, String expirationDate) {
        super(
                String.format("Card %s expired on %s", cardId, expirationDate),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
        this.addMetadata("expirationDate", expirationDate);
    }
}
