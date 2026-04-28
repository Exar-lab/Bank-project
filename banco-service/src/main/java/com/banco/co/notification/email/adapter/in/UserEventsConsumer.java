package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.dto.WelcomeEmailContext;
import com.banco.co.notification.email.service.IEmailService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class UserEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventsConsumer.class);
    private static final String USER_CREATED = "UserCreated";

    private final ObjectMapper objectMapper;
    private final IEmailService emailService;

    public UserEventsConsumer(ObjectMapper objectMapper, IEmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @KafkaListener(
            topics = "banco.user.events",
            groupId = "banco-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(@Payload String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (JacksonException ex) {
            log.error("UserEventsConsumer failed to parse payload", ex);
            return;
        }

        String eventType = node.path("eventType").asText();
        if (!USER_CREATED.equals(eventType)) {
            return;
        }

        String eventId = node.path("eventId").asText();
        String userIdRaw = node.path("userId").asText();
        UUID userId;
        try {
            userId = UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            log.warn("UserEventsConsumer ignored event because userId is invalid");
            return;
        }
        String recipientEmail = node.path("email").asText();
        String recipientName = node.path("recipientName").asText();
        String userCode = node.path("userCode").asText();

        WelcomeEmailContext context = new WelcomeEmailContext(recipientName, userCode, "Banco CO");
        Map<String, Object> templateContext = Map.of(
                "recipientName", context.recipientName(),
                "userCode", context.userCode(),
                "bankName", context.bankName()
        );

        emailService.enqueue(
                eventId,
                userId,
                recipientEmail,
                recipientName,
                "email/welcome",
                templateContext,
                "Bienvenido/a a Banco CO"
        );
    }
}
