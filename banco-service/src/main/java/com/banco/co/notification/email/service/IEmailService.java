package com.banco.co.notification.email.service;

import java.util.Map;
import java.util.UUID;

public interface IEmailService {
    void enqueue(
            String eventId,
            UUID userId,
            String recipientEmail,
            String recipientName,
            String templateName,
            Map<String, Object> templateContext,
            String subject
    );
}
