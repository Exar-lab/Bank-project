package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.service.IEmailService;
import com.banco.co.outbox.config.KafkaConsumerConfig;
import com.banco.co.outbox.config.KafkaProducerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {
                UserEventsConsumer.class,
                KafkaProducerConfig.class,
                KafkaConsumerConfig.class,
                UserEventsConsumerIntegrationTest.TestKafkaBeans.class
        },
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.task.scheduling.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
@EmbeddedKafka(partitions = 1, topics = {"banco.user.events"})
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserEventsConsumerIntegrationTest {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private IEmailService emailService;

    UserEventsConsumerIntegrationTest(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Test
    void testConsume_WhenUserCreatedEvent_ThenEnqueuesWelcomeEmail() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "evt-100");
        payload.put("userId", UUID.randomUUID().toString());
        payload.put("eventType", "UserCreated");
        payload.put("email", "test@banco.co");
        payload.put("recipientName", "Juan");
        payload.put("userCode", "USR-100");

        kafkaTemplate.send("banco.user.events", objectMapper.writeValueAsString(payload));
        TimeUnit.SECONDS.sleep(1);

        verify(emailService, timeout(3000)).enqueue(
                eq("evt-100"),
                eq(UUID.fromString(payload.get("userId").toString())),
                eq("test@banco.co"),
                eq("Juan"),
                eq("email/welcome"),
                anyMap(),
                eq("Bienvenido/a a Banco CO")
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class TestKafkaBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
