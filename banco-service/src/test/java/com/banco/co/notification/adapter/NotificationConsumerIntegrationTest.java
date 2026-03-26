package com.banco.co.notification.adapter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"banco.envelope.events"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9094",
                "port=9094"
        }
)
@DirtiesContext
class NotificationConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger notificationLogger;

    @BeforeEach
    void setUp() {
        notificationLogger = (Logger) LoggerFactory.getLogger(NotificationConsumer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        notificationLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        notificationLogger.detachAppender(listAppender);
    }

    @Test
    void testConsume_EnvelopeGoalReachedEvent_LogsNotificationIntent() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "EnvelopeGoalReached");
        payload.put("envelopeCode", "ENV-001");
        payload.put("goalAmount", "5000000");

        kafkaTemplate.send("banco.envelope.events", objectMapper.writeValueAsString(payload));

        boolean found = false;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (System.currentTimeMillis() < deadline) {
            found = listAppender.list.stream()
                    .anyMatch(event ->
                            event.getLevel() == Level.INFO &&
                            event.getFormattedMessage().contains("NOTIFICATION") &&
                            event.getFormattedMessage().contains("ENV-001"));
            if (found) break;
            TimeUnit.MILLISECONDS.sleep(500);
        }

        assertThat(found).isTrue();
    }

    @Test
    void testConsume_NonGoalReachedEvent_SkipsNotification() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "EnvelopeCreated");
        payload.put("envelopeCode", "ENV-002");

        kafkaTemplate.send("banco.envelope.events", objectMapper.writeValueAsString(payload));

        // Give the consumer time to process
        TimeUnit.SECONDS.sleep(2);

        boolean found = listAppender.list.stream()
                .anyMatch(event ->
                        event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("NOTIFICATION"));
        assertThat(found).isFalse();
    }
}
