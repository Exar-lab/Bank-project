package com.banco.co.notification.email.relay;

import com.banco.co.notification.email.config.MailProperties;
import com.banco.co.notification.email.dto.EmailMessage;
import com.banco.co.notification.email.exception.NotificationException;
import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.model.EmailOutboxStatus;
import com.banco.co.notification.email.port.IEmailDispatcher;
import com.banco.co.notification.email.port.IEmailOutboxRepository;
import com.banco.co.notification.email.port.IEmailTemplateRenderer;
import com.banco.co.notification.email.service.EmailAuditPublisher;
import com.banco.co.notification.email.service.RecipientHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EmailOutboxDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatchWorker.class);

    private final IEmailOutboxRepository emailOutboxRepository;
    private final IEmailTemplateRenderer templateRenderer;
    private final IEmailDispatcher emailDispatcher;
    private final RecipientHasher recipientHasher;
    private final EmailAuditPublisher emailAuditPublisher;
    private final ObjectMapper objectMapper;
    private final MailProperties mailProperties;

    public EmailOutboxDispatchWorker(
            IEmailOutboxRepository emailOutboxRepository,
            IEmailTemplateRenderer templateRenderer,
            IEmailDispatcher emailDispatcher,
            RecipientHasher recipientHasher,
            EmailAuditPublisher emailAuditPublisher,
            ObjectMapper objectMapper,
            MailProperties mailProperties
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.templateRenderer = templateRenderer;
        this.emailDispatcher = emailDispatcher;
        this.recipientHasher = recipientHasher;
        this.emailAuditPublisher = emailAuditPublisher;
        this.objectMapper = objectMapper;
        this.mailProperties = mailProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchSafely(Long eventId) {
        emailOutboxRepository.findByIdForUpdate(eventId).ifPresent(event -> {
            if (event.getStatus() != EmailOutboxStatus.PROCESSING) {
                return;
            }

            if (!mailProperties.enabled()) {
                event.setStatus(EmailOutboxStatus.PENDING);
                event.setClaimedBy(null);
                emailOutboxRepository.save(event);
                return;
            }

            try {
                Map<String, Object> context = objectMapper.readValue(
                        event.getTemplateContextJson(),
                        new TypeReference<>() {
                        }
                );

                String htmlBody = templateRenderer.render(event.getTemplateName(), context);
                EmailMessage message = new EmailMessage(
                        event.getEventId(),
                        event.getRecipientEmail(),
                        event.getSubject(),
                        htmlBody
                );
                emailDispatcher.dispatch(message);

                event.setStatus(EmailOutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setClaimedBy(null);
                event.setLastError(null);
                emailOutboxRepository.save(event);

                String recipientHash = recipientHasher.hash(event.getRecipientEmail());
                emailAuditPublisher.logSent(event.getUserId(), recipientHash);

                log.info("event=email_outbox_transition eventId={} templateName={} status={} attemptCount={}",
                        event.getEventId(), event.getTemplateName(), event.getStatus(), event.getAttemptCount());
            } catch (JsonProcessingException ex) {
                handleFailure(event, ex);
            } catch (NotificationException ex) {
                handleFailure(event, ex);
            }
        });
    }

    private void handleFailure(EmailOutboxEvent event, Exception ex) {
        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);
        event.setLastError(ex.getClass().getSimpleName());
        event.setClaimedBy(null);

        if (nextAttempt >= mailProperties.relay().maxAttempts()) {
            event.setStatus(EmailOutboxStatus.DEAD);
            emailOutboxRepository.save(event);
            emailAuditPublisher.logDead(event.getEventId(), nextAttempt, event.getTemplateName());
            log.warn("event=email_outbox_transition eventId={} templateName={} status={} attemptCount={}",
                    event.getEventId(), event.getTemplateName(), event.getStatus(), event.getAttemptCount());
            return;
        }

        event.setStatus(EmailOutboxStatus.PENDING);
        event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.max(60L, (long) Math.pow(2, nextAttempt) * 60L)));
        emailOutboxRepository.save(event);

        log.warn("event=email_outbox_transition eventId={} templateName={} status={} attemptCount={}",
                event.getEventId(), event.getTemplateName(), event.getStatus(), event.getAttemptCount());
    }
}
