package com.banco.co.notification.email.dto;

public record EmailMessage(
        String eventId,
        String recipientEmail,
        String subject,
        String htmlBody
) {
}
