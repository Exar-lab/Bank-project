package com.banco.co.card.exception.card;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class DailyLimitExceededException extends CardException {

    private static final String ERROR_CODE = "DAILY_LIMIT_EXCEEDED";
    private static final HttpStatus STATUS = HttpStatus.TOO_MANY_REQUESTS;

    public DailyLimitExceededException(String cardId, BigDecimal dailyLimit) {
        super(
                String.format("Daily transaction limit exceeded for card %s. Limit: %s", cardId, dailyLimit),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
        this.addMetadata("dailyLimit", dailyLimit);
    }

    public DailyLimitExceededException(String cardId, BigDecimal requested, BigDecimal remaining) {
        super(
                String.format("Daily limit exceeded for card %s. Requested: %s, Remaining: %s",
                        cardId, requested, remaining),
                ERROR_CODE,
                STATUS);
        this.addMetadata("cardId", cardId);
        this.addMetadata("requestedAmount", requested);
        this.addMetadata("remainingLimit", remaining);
    }
}
