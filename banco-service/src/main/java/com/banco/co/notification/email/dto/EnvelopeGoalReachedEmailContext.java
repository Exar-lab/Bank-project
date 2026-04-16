package com.banco.co.notification.email.dto;

public record EnvelopeGoalReachedEmailContext(
        String recipientName,
        String envelopeCode,
        String goalAmount,
        String bankName
) {
}
