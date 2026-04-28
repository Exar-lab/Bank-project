package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.dto.EnvelopeGoalReachedEmailContext;
import com.banco.co.notification.email.service.IEmailService;
import com.banco.co.user.model.User;
import com.banco.co.user.repository.IUserRepository;
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
public class EnvelopeEventsEmailHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventsEmailHandler.class);
    private static final String ENVELOPE_GOAL_REACHED = "EnvelopeGoalReached";

    private final ObjectMapper objectMapper;
    private final IUserRepository userRepository;
    private final IEmailService emailService;

    public EnvelopeEventsEmailHandler(
            ObjectMapper objectMapper,
            IUserRepository userRepository,
            IEmailService emailService
    ) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @KafkaListener(
            topics = "banco.envelope.events",
            groupId = "banco-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(@Payload String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (JacksonException ex) {
            log.error("EnvelopeEventsEmailHandler failed to parse payload", ex);
            return;
        }

        String eventType = node.path("eventType").asText();
        if (!ENVELOPE_GOAL_REACHED.equals(eventType)) {
            return;
        }

        String userIdRaw = node.path("userId").asText();
        UUID userId;
        try {
            userId = UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            log.warn("EnvelopeEventsEmailHandler ignored event because userId is invalid");
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("EnvelopeEventsEmailHandler ignored event because user not found");
            return;
        }

        String eventId = node.path("eventId").asText();
        String envelopeCode = node.path("envelopeCode").asText();
        String goalAmount = node.path("goalAmount").asText();

        EnvelopeGoalReachedEmailContext context = new EnvelopeGoalReachedEmailContext(
                user.getFistName(),
                envelopeCode,
                goalAmount,
                "Banco CO"
        );

        Map<String, Object> templateContext = Map.of(
                "recipientName", context.recipientName(),
                "envelopeCode", context.envelopeCode(),
                "goalAmount", context.goalAmount(),
                "bankName", context.bankName()
        );

        emailService.enqueue(
                eventId,
                userId,
                user.getEmail(),
                user.getFistName(),
                "email/envelope-goal-reached",
                templateContext,
                "Has alcanzado tu meta de ahorro"
        );
    }
}
