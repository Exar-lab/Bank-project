package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

public class CardNotActiveException extends CardException {

    private static final String ERROR_CODE = "CARD_NOT_ACTIVE";
    private static final HttpStatus STATUS = HttpStatus.PRECONDITION_FAILED;

    public CardNotActiveException(String cardId) {
        super(
                String.format("Card %s is not active", cardId),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
    }

    public CardNotActiveException(String cardId, String currentStatus) {
        super(
                String.format("Card %s is not active. Current status: %s", cardId, currentStatus),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
        this.addMetadata("currentStatus", currentStatus);
    }
}
