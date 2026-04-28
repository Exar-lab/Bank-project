package com.banco.co.notification.email.service;

import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.port.IEmailOutboxRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private IEmailOutboxRepository emailOutboxRepository;
    @Mock
    private EmailAuditPublisher emailAuditPublisher;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(emailOutboxRepository, new ObjectMapper(), emailAuditPublisher);
    }

    @Test
    void testEnqueue_WhenValidPayload_ThenPersistsOutboxEvent() {
        Map<String, Object> context = Map.of("recipientName", "Juan");
        UUID userId = UUID.randomUUID();

        emailService.enqueue("evt-1", userId, "juan@banco.co", "Juan", "email/welcome", context, "Bienvenido");

        ArgumentCaptor<EmailOutboxEvent> captor = ArgumentCaptor.forClass(EmailOutboxEvent.class);
        verify(emailOutboxRepository).save(captor.capture());

        EmailOutboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-1");
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getRecipientEmail()).isEqualTo("juan@banco.co");
        assertThat(saved.getTemplateName()).isEqualTo("email/welcome");
        assertThat(saved.getSubject()).isEqualTo("Bienvenido");
    }

    @Test
    void testEnqueue_WhenDuplicateEventId_ThenAuditsDedupAndSkipsFailure() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(emailOutboxRepository).save(any(EmailOutboxEvent.class));

        emailService.enqueue("evt-dup", UUID.randomUUID(), "juan@banco.co", "Juan", "email/welcome", Map.of(), "Bienvenido");

        verify(emailAuditPublisher).logDeduped("evt-dup");
    }

    @Test
    void testEnqueue_WhenContextSerializationFails_ThenThrowsIllegalState() {
        // Force serialization failure with cyclic reference
        @SuppressWarnings("unchecked")
        Map<String, Object> cyclic = (Map<String, Object>) (Map<?, ?>) new java.util.HashMap<>();
        cyclic.put("self", cyclic);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> emailService.enqueue("evt-err", UUID.randomUUID(), "juan@banco.co", "Juan", "email/welcome", cyclic, "Bienvenido")
        );
        verify(emailOutboxRepository, never()).save(any(EmailOutboxEvent.class));
    }
}
