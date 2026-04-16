package com.banco.co.notification.email.dto;

public record WelcomeEmailContext(
        String recipientName,
        String userCode,
        String bankName
) {
}
