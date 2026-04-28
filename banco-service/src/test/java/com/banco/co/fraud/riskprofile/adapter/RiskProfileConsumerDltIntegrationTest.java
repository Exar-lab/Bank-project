package com.banco.co.fraud.riskprofile.adapter;

import com.banco.co.fraud.riskprofile.service.IRiskProfileAsyncUpdaterService;
import com.banco.co.outbox.config.KafkaConsumerConfig;
import com.banco.co.outbox.config.KafkaProducerConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {
                RiskProfileConsumer.class,
                KafkaProducerConfig.class,
                KafkaConsumerConfig.class,
                RiskProfileConsumerDltIntegrationTest.TestKafkaBeans.class
        },
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.task.scheduling.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
@EmbeddedKafka(
        partitions = 1,
        topics = {"banco.transaction.events", "banco.transaction.events.DLT"}
)
@DirtiesContext
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RiskProfileConsumerDltIntegrationTest {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    private IRiskProfileAsyncUpdaterService asyncUpdaterService;

    RiskProfileConsumerDltIntegrationTest(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            EmbeddedKafkaBroker embeddedKafkaBroker
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.embeddedKafkaBroker = embeddedKafkaBroker;
    }

    @Test
    void testConsume_WhenProcessableFailure_RetriesAndPublishesToDlt() throws Exception {
        when(asyncUpdaterService.updateFromTransactionCompleted(any()))
                .thenThrow(new RuntimeException("simulated-db-timeout"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "TransactionCompleted");
        payload.put("eventId", "evt-dlt-001");
        payload.put("transactionId", "tx-dlt-001");
        payload.put("fromAccountCode", "ACC-DLT-001");
        payload.put("amount", "60000000");
        payload.put("currency", "CRC");
        payload.put("status", "COMPLETED");

        try (Consumer<String, String> dltConsumer = createDltConsumer()) {
            dltConsumer.subscribe(java.util.List.of("banco.transaction.events.DLT"));

            kafkaTemplate.send("banco.transaction.events", objectMapper.writeValueAsString(payload)).get();

            ConsumerRecord<String, String> dltRecord = awaitSingleRecord(dltConsumer, Duration.ofSeconds(20));
            assertThat(dltRecord).isNotNull();
            JsonNode dltEvent = objectMapper.readTree(dltRecord.value());
            assertThat(dltEvent.path("eventId").asText()).isEqualTo("evt-dlt-001");
            assertThat(dltEvent.path("transactionId").asText()).isEqualTo("tx-dlt-001");
            assertThat(dltEvent.path("fromAccountCode").asText()).isEqualTo("ACC-DLT-001");
        }

        verify(asyncUpdaterService, timeout(15_000).atLeast(4))
                .updateFromTransactionCompleted(any());
    }

    private Consumer<String, String> createDltConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "risk-profile-dlt-verifier");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private ConsumerRecord<String, String> awaitSingleRecord(Consumer<String, String> consumer, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("Timed out waiting for DLT record on banco.transaction.events.DLT");
    }

    @Configuration(proxyBeanMethods = false)
    static class TestKafkaBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
