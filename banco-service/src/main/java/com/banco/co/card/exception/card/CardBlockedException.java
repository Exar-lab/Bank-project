package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

public class CardBlockedException extends CardException {

    private static final String ERROR_CODE = "CARD_BLOCKED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

    public CardBlockedException(String cardId) {
        super(
                String.format("Card %s is blocked", cardId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
    }

    public CardBlockedException(String cardId, String reason) {
        super(
                String.format("Card %s is blocked. Reason: %s", cardId, reason),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
        this.addMetadata("reason", reason);
    }
}
