package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

public class CardClosedException extends CardException {

    private static final String ERROR_CODE = "CARD_CLOSED";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public CardClosedException(String cardCode) {
        super(
                "Card " + cardCode + " is already closed",
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardCode", cardCode);
    }
}
