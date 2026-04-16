package com.banco.co.notification.email.service;

import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.port.IEmailOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class EmailServiceImpl implements IEmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final IEmailOutboxRepository emailOutboxRepository;
    private final ObjectMapper objectMapper;
    private final EmailAuditPublisher emailAuditPublisher;

    public EmailServiceImpl(
            IEmailOutboxRepository emailOutboxRepository,
            ObjectMapper objectMapper,
            EmailAuditPublisher emailAuditPublisher
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.objectMapper = objectMapper;
        this.emailAuditPublisher = emailAuditPublisher;
    }

    @Override
    @Transactional
    public void enqueue(
            String eventId,
            UUID userId,
            String recipientEmail,
            String recipientName,
            String templateName,
            Map<String, Object> templateContext,
            String subject
    ) {
        try {
            String contextJson = objectMapper.writeValueAsString(templateContext);
            EmailOutboxEvent event = new EmailOutboxEvent(
                    eventId,
                    userId,
                    recipientEmail,
                    recipientName,
                    templateName,
                    contextJson,
                    subject
            );
            emailOutboxRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            log.warn("EMAIL_ENQUEUE_DEDUPED eventId={}", eventId);
            emailAuditPublisher.logDeduped(eventId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize email context for event " + eventId, ex);
        }
    }
}
