package com.banco.co.notification.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private static final String ENVELOPE_GOAL_REACHED = "EnvelopeGoalReached";

    private final ObjectMapper objectMapper;

    public NotificationConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "banco.envelope.events",
            groupId = "banco-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("NotificationConsumer: failed to parse payload. Raw: {}", payload, e);
            return; // malformed JSON — skip, do not retry
        }

        String eventType = node.path("eventType").asText();
        if (!ENVELOPE_GOAL_REACHED.equals(eventType)) {
            log.debug("NotificationConsumer: ignoring event type '{}'", eventType);
            return;
        }

        String envelopeCode = node.path("envelopeCode").asText();
        String goalAmount = node.path("goalAmount").asText();

        log.info("NOTIFICATION: Envelope {} reached savings goal of {}. User notification pending.",
                envelopeCode, goalAmount);
    }
}
