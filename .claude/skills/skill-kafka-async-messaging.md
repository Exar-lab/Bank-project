---
name: skill-kafka-async-messaging
description: >
  Kafka async event publishing via transactional outbox pattern for banco-service.
  Covers KafkaTemplate producer setup, OutboxEntry persistence, OutboxScheduler polling,
  Dead Letter Topic error handling, and @KafkaListener consumer patterns.
  Trigger: When implementing event publishing, outbox persistence, Kafka producers/consumers,
  or the OutboxScheduler in the infrastructure layer.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Creating a Kafka event publisher (`KafkaEventPublisher`)
- Implementing the transactional outbox pattern (`OutboxEntry`, `OutboxEntity`, `OutboxRepository`)
- Writing the outbox scheduler (`OutboxScheduler`)
- Configuring Kafka producer/consumer beans (`KafkaConfig`)
- Defining domain event Records (`AccountCreatedEvent`, `TransactionCompletedEvent`, etc.)
- Setting up Dead Letter Topics or consumer error handling
- Adding `spring-kafka` dependencies to `pom.xml`
- Adding Kafka configuration to `application.yml`

**Decision Tree**:
- Need to publish an event after a domain operation? → Use Transactional Outbox (Pattern 1 + 2)
- Need to define what goes inside the event? → Use Event Records (Pattern 3)
- Need to publish from the scheduler to Kafka? → Use KafkaTemplate (Pattern 4)
- Need to consume events in this service? → Use @KafkaListener (Pattern 5)
- Need to handle consumer failures safely? → Use Dead Letter Topic config (Pattern 6)

---

## Architecture Context

banco-service uses the **Transactional Outbox Pattern** — NOT dual-write, NOT CDC.

```
REST Request
  │
  ▼
Application Service (@Transactional)
  ├─ accountRepository.save(account)    ← domain entity persisted
  └─ outboxRepository.save(outboxEntry) ← event persisted atomically
  │
  └─ HTTP 201 Created (immediate, no Kafka wait)

[Async — separate thread]
OutboxScheduler (@Scheduled, every 1 second)
  ├─ SELECT * FROM account_events_outbox WHERE published = false LIMIT 100
  ├─ KafkaTemplate.send(topic, key, payload)
  └─ UPDATE SET published = true (only on success)
```

**Why Outbox over Dual-Write**: If the service crashes between the DB write and the Kafka publish,
dual-write loses the event. Outbox keeps the event in the DB and retries until Kafka confirms delivery.

**Delivery guarantee**: At-least-once. Consumers MUST be idempotent using `eventId` as dedup key.

**Topic naming**: `banco.{feature}.{event-type}` — e.g., `banco.account.created`, `banco.transaction.completed`

**Layer where Kafka code lives**: Infrastructure (`com.banco.co.infrastructure.kafka.*`, `com.banco.co.infrastructure.scheduler.*`)

---

## Critical Patterns

### ✅ Pattern 1: OutboxEntity — JPA Entity for the Outbox Table

```java
// com/banco/co/infrastructure/persistence/OutboxEntity.java
package com.banco.co.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "account_events_outbox",
    indexes = {
        @Index(name = "idx_outbox_published_created", columnList = "published, created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id, aggregate_type")
    }
)
@Getter
@NoArgsConstructor
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType; // "Account", "Transaction", "Envelope"

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // "account.created", "transaction.completed", "envelope.created"

    @Column(name = "event_version", nullable = false)
    private int eventVersion = 1;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload; // JSON-serialized event record

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    // Factory method — use this, not a public constructor
    public static OutboxEntity of(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String jsonPayload) {
        OutboxEntity entry = new OutboxEntity();
        entry.aggregateId = aggregateId;
        entry.aggregateType = aggregateType;
        entry.eventType = eventType;
        entry.payload = jsonPayload;
        return entry;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public void recordFailure(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage != null
            ? errorMessage.substring(0, Math.min(errorMessage.length(), 500))
            : "Unknown error";
    }
}
```

**Why this works**:
- `@Index` covers both scheduler polling (`published, created_at`) and business queries (`aggregate_id`)
- `columnDefinition = "BINARY(16)"` for UUIDs in MySQL (compact, indexed efficiently)
- `payload` stored as `JSON` column type — MySQL validates JSON at write time
- `markPublished()` and `recordFailure()` mutate state — entity owns its transitions
- Factory method `of(...)` prevents incomplete construction

---

### ✅ Pattern 2: Service — Atomic Domain + Outbox Persistence

```java
// com/banco/co/account/service/AccountService.java
package com.banco.co.account.service;

import com.banco.co.account.dto.CreateAccountRequestDto;
import com.banco.co.account.dto.CreateAccountResponseDto;
import com.banco.co.account.event.AccountCreatedEvent;
import com.banco.co.account.mapper.IAccountMapper;
import com.banco.co.account.model.Account;
import com.banco.co.account.repository.IAccountRepository;
import com.banco.co.infrastructure.outbox.IOutboxRepository;
import com.banco.co.infrastructure.persistence.OutboxEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    private final IAccountRepository accountRepository;
    private final IOutboxRepository outboxRepository;
    private final IAccountMapper accountMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional  // ← Single transaction: account + outbox entry are atomic
    public CreateAccountResponseDto createAccount(CreateAccountRequestDto dto, UUID userId) {
        // 1. Domain operation
        Account account = new Account(dto.accountHolder(), dto.accountType(), dto.initialBalance(), userId);
        Account saved = accountRepository.save(account);

        // 2. Build the event record
        AccountCreatedEvent event = new AccountCreatedEvent(
            UUID.randomUUID(),
            Instant.now(),
            "account.created",
            1,
            saved.getId(),
            new AccountCreatedEvent.Payload(
                saved.getId(),
                userId,
                saved.getAccountHolder(),
                saved.getAccountType().name(),
                saved.getBalance(),
                saved.getAccountCode(),
                saved.getStatus().name(),
                saved.getCreatedAt()
            )
        );

        // 3. Serialize and persist to outbox (same transaction as above)
        String payload = serializeEvent(event);
        OutboxEntity outboxEntry = OutboxEntity.of(
            saved.getId(),
            "Account",
            "account.created",
            payload
        );
        outboxRepository.save(outboxEntry);

        log.debug("Account {} created, outbox entry {} queued for publishing",
            saved.getId(), outboxEntry.getId());

        return accountMapper.toCreateResponseDto(saved, event.eventId());
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Failed to serialize event", e);
        }
    }
}
```

**Why this works**:
- Single `@Transactional` block ensures account + outbox entry succeed or fail together
- No Kafka call in the request path — response is immediate
- `objectMapper.writeValueAsString(event)` serializes the Record to JSON
- If Kafka is down, the account is still created and the event waits safely in the DB

---

### ✅ Pattern 3: Domain Event Records — Immutable Event DTOs

```java
// com/banco/co/account/event/AccountCreatedEvent.java
package com.banco.co.account.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountCreatedEvent(
    UUID eventId,
    Instant timestamp,
    String eventType,       // "account.created"
    int eventVersion,       // 1
    UUID aggregateId,       // accountId
    Payload payload
) {
    public record Payload(
        UUID accountId,
        UUID userId,
        String accountHolder,
        String accountType,
        BigDecimal balance,
        String accountCode,
        String status,
        Instant createdAt
    ) {}
}

// com/banco/co/transaction/event/TransactionCompletedEvent.java
package com.banco.co.transaction.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
    UUID eventId,
    Instant timestamp,
    String eventType,       // "transaction.completed"
    int eventVersion,
    UUID aggregateId,       // transactionId
    Payload payload
) {
    public record Payload(
        UUID transactionId,
        String type,         // TRANSFER, DEPOSIT, WITHDRAWAL
        String status,       // COMPLETED, FAILED
        UUID fromAccountId,  // nullable for deposits
        UUID toAccountId,    // nullable for withdrawals
        BigDecimal amount,
        String description,
        Instant completedAt
    ) {}
}

// com/banco/co/envelope/event/EnvelopeCreatedEvent.java
package com.banco.co.envelope.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnvelopeCreatedEvent(
    UUID eventId,
    Instant timestamp,
    String eventType,       // "envelope.created"
    int eventVersion,
    UUID aggregateId,       // envelopeId
    Payload payload
) {
    public record Payload(
        UUID envelopeId,
        UUID accountId,
        String name,
        String type,
        BigDecimal goalAmount,
        BigDecimal currentAmount,
        Instant createdAt
    ) {}
}
```

**Why this works**:
- Records are immutable — no accidental mutation after construction
- Nested `Payload` record keeps the structure flat and readable
- Jackson serializes Java Records directly to JSON without extra config (Spring Boot 4.x)
- `eventVersion` allows schema evolution without breaking consumers

---

### ✅ Pattern 4: KafkaEventPublisher — Infrastructure Adapter

```java
// com/banco/co/infrastructure/kafka/KafkaEventPublisher.java
package com.banco.co.infrastructure.kafka;

import com.banco.co.infrastructure.persistence.OutboxEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    // Topic routing: event_type → Kafka topic
    private static final Map<String, String> TOPIC_ROUTING = Map.of(
        "account.created",          "banco.account.created",
        "transaction.completed",    "banco.transaction.completed",
        "envelope.created",         "banco.envelope.created"
    );

    /**
     * Publishes an outbox entry to the corresponding Kafka topic.
     * Partition key = aggregateId (ensures ordering per aggregate).
     *
     * @throws EventPublishingException if the topic is unknown or Kafka send fails
     */
    public void publish(OutboxEntity entry) {
        String topic = resolveTopic(entry.getEventType());
        String partitionKey = entry.getAggregateId().toString();

        try {
            SendResult<String, String> result = kafkaTemplate
                .send(topic, partitionKey, entry.getPayload())
                .get(); // block until broker acknowledges (acks=all in config)

            log.info("Published event {} [{}] to topic {} partition {} offset {}",
                entry.getId(),
                entry.getEventType(),
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Interrupted while publishing event " + entry.getId(), e);
        } catch (ExecutionException e) {
            throw new EventPublishingException(
                "Failed to publish event " + entry.getId() + " to topic " + topic, e.getCause());
        }
    }

    private String resolveTopic(String eventType) {
        String topic = TOPIC_ROUTING.get(eventType);
        if (topic == null) {
            throw new EventPublishingException(
                "No topic configured for event type: " + eventType);
        }
        return topic;
    }
}
```

**Why this works**:
- `kafkaTemplate.send(...).get()` blocks until the broker acknowledges — prevents marking `published=true` on a lost send
- Partition key = `aggregateId` ensures all events for the same Account arrive in order
- Topic routing via `Map.of(...)` is explicit and easy to extend
- `InterruptedException` re-interrupts the thread — required for proper thread lifecycle

---

### ✅ Pattern 5: OutboxScheduler — Polling and Batch Publishing

```java
// com/banco/co/infrastructure/scheduler/OutboxScheduler.java
package com.banco.co.infrastructure.scheduler;

import com.banco.co.infrastructure.kafka.EventPublishingException;
import com.banco.co.infrastructure.kafka.KafkaEventPublisher;
import com.banco.co.infrastructure.outbox.IOutboxRepository;
import com.banco.co.infrastructure.persistence.OutboxEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxScheduler {

    private final IOutboxRepository outboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${outbox.scheduler.batch-size:100}")
    private int batchSize;

    @Value("${outbox.scheduler.max-retry-count:10}")
    private int maxRetryCount;

    /**
     * Polls unpublished outbox entries every second, publishes batch to Kafka.
     * fixedDelay ensures the NEXT poll starts 1 second after the PREVIOUS one completes
     * (vs fixedRate which could overlap if publishing is slow).
     */
    @Scheduled(fixedDelayString = "${outbox.scheduler.fixed-delay-ms:1000}",
               initialDelayString = "${outbox.scheduler.initial-delay-ms:5000}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEntity> unpublished = outboxRepository
            .findUnpublishedBatch(batchSize, maxRetryCount);

        if (unpublished.isEmpty()) {
            log.debug("Outbox poll: no pending events");
            return;
        }

        log.debug("Outbox poll: processing {} events", unpublished.size());
        int successCount = 0;
        int failureCount = 0;

        for (OutboxEntity entry : unpublished) {
            try {
                kafkaEventPublisher.publish(entry);
                entry.markPublished();
                successCount++;
            } catch (EventPublishingException ex) {
                entry.recordFailure(ex.getMessage());
                failureCount++;
                log.error("Failed to publish event {} [{}]: {}",
                    entry.getId(), entry.getEventType(), ex.getMessage());
            }
        }

        outboxRepository.saveAll(unpublished);
        log.info("Outbox cycle complete: {} published, {} failed", successCount, failureCount);
    }
}
```

**Why this works**:
- `fixedDelay` (not `fixedRate`) prevents overlapping polls if Kafka is slow
- Per-entry try/catch: one failure does not block the other 99 entries in the batch
- `entry.recordFailure()` increments `retry_count` — scheduler stops retrying after `max-retry-count`
- `@Transactional` on the scheduler method: all `markPublished()` / `recordFailure()` updates commit together
- `initialDelay` of 5 seconds prevents the scheduler from firing during application startup

---

### ✅ Pattern 6: OutboxRepository — Custom Query for Scheduler

```java
// com/banco/co/infrastructure/outbox/IOutboxRepository.java
package com.banco.co.infrastructure.outbox;

import com.banco.co.infrastructure.persistence.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IOutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    /**
     * Fetches unpublished entries eligible for retry.
     * Excludes entries that have exceeded max retry count (they need manual review).
     */
    @Query("""
        SELECT o FROM OutboxEntity o
        WHERE o.published = false
          AND o.retryCount < :maxRetryCount
        ORDER BY o.createdAt ASC
        LIMIT :batchSize
    """)
    List<OutboxEntity> findUnpublishedBatch(
        @Param("batchSize") int batchSize,
        @Param("maxRetryCount") int maxRetryCount
    );

    /**
     * For monitoring: count stale entries exceeding retry limit.
     */
    @Query("SELECT COUNT(o) FROM OutboxEntity o WHERE o.published = false AND o.retryCount >= :maxRetryCount")
    long countStaleEntries(@Param("maxRetryCount") int maxRetryCount);
}
```

**Why this works**:
- `LIMIT :batchSize` caps the scheduler batch — prevents memory spike with huge backlogs
- `retryCount < :maxRetryCount` filter excludes permanently failed events — prevents infinite retry loops
- `ORDER BY createdAt ASC` ensures FIFO processing — oldest events publish first
- `countStaleEntries` allows a health check or alerting endpoint to report events needing human intervention

---

### ✅ Pattern 7: KafkaConfig — Producer Bean Configuration

```java
// com/banco/co/infrastructure/kafka/KafkaConfig.java
package com.banco.co.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class, // payload is pre-serialized JSON string
            ProducerConfig.ACKS_CONFIG, "all",          // wait for all replicas
            ProducerConfig.RETRIES_CONFIG, 3,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1, // preserve ordering on retry
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        ));
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Topic beans — auto-creates if missing on startup (useful for dev; disable in prod)
    @Bean
    public NewTopic accountCreatedTopic() {
        return TopicBuilder.name("banco.account.created")
            .partitions(3)
            .replicas(1)   // 1 for dev, 3 for prod
            .build();
    }

    @Bean
    public NewTopic transactionCompletedTopic() {
        return TopicBuilder.name("banco.transaction.completed")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic envelopeCreatedTopic() {
        return TopicBuilder.name("banco.envelope.created")
            .partitions(3)
            .replicas(1)
            .build();
    }
}
```

**Why this works**:
- `StringSerializer` for value: payload is already a JSON string (serialized in service layer) — no double-serialization
- `ACKS_CONFIG = "all"` + `MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = 1` + `ENABLE_IDEMPOTENCE_CONFIG = true` — strongest producer durability guarantee without Kafka Transactions
- `TopicBuilder` with 3 partitions enables parallel consumer processing and ordering per `aggregateId` key

---

### ✅ Pattern 8: @KafkaListener — Consumer Pattern (when banco-service consumes its own events)

```java
// com/banco/co/fraud/listener/TransactionEventListener.java
package com.banco.co.fraud.listener;

import com.banco.co.transaction.event.TransactionCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionEventListener {

    private final FraudDetectionService fraudDetectionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        id = "fraud-detection-listener",
        topics = "banco.transaction.completed",
        groupId = "banco-fraud-detection",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionCompleted(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        try {
            TransactionCompletedEvent event = objectMapper.readValue(
                record.value(), TransactionCompletedEvent.class);

            log.debug("Received transaction event {} for aggregate {}",
                event.eventId(), event.aggregateId());

            // Idempotency check: skip if already processed
            if (fraudDetectionService.alreadyProcessed(event.eventId())) {
                log.info("Duplicate event {} skipped (already processed)", event.eventId());
                ack.acknowledge(); // ack to advance offset — do NOT reprocess
                return;
            }

            fraudDetectionService.analyze(event);
            ack.acknowledge(); // ack only after successful processing

        } catch (Exception ex) {
            log.error("Failed to process transaction event from partition {} offset {}: {}",
                record.partition(), record.offset(), ex.getMessage());
            // Do NOT ack → DefaultErrorHandler takes over → retries → DLT after max retries
        }
    }
}
```

**Why this works**:
- `Acknowledgment ack` with manual mode: consumer controls offset commits — no silent data loss
- Idempotency check prevents duplicate processing (at-least-once delivery means duplicates will happen)
- Not acking on exception lets `DefaultErrorHandler` retry the record, then route to DLT
- `ConsumerRecord<String, String>` gives access to headers, partition, offset for debugging

---

### ✅ Pattern 9: Dead Letter Topic — Consumer Error Handling Config

```java
// com/banco/co/infrastructure/kafka/KafkaConsumerConfig.java
package com.banco.co.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,  // manual ack
            ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"
        ));
    }

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // Route failed records to "{original-topic}.DLT"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> {
                log.error("Routing event to DLT: topic={}, partition={}, error={}",
                    record.topic(), record.partition(), ex.getMessage());
                return new TopicPartition(record.topic() + ".DLT", record.partition());
            }
        );

        // Retry 3 times with exponential backoff: 1s → 2s → 4s → DLT
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Validation errors are not retryable — go directly to DLT
        handler.addNotRetryableExceptions(
            com.fasterxml.jackson.core.JsonProcessingException.class,
            IllegalArgumentException.class
        );

        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3); // 3 consumer threads per listener

        return factory;
    }
}
```

**Why this works**:
- `ExponentialBackOff(1000, 2.0).maxAttempts(3)` retries at 1s, 2s, 4s then routes to DLT — prevents hot-looping on bad records
- `DeadLetterPublishingRecoverer` appends `.DLT` to the topic name — easy to subscribe for manual review
- `JsonProcessingException` as non-retryable: a malformed payload will never succeed — skip retries, go straight to DLT
- `AckMode.MANUAL` paired with explicit `ack.acknowledge()` in the listener — no silent offset advances

---

### ❌ Common Mistake 1: Dual-Write (Direct Kafka Call in Service)

```java
// ❌ WRONG — this is dual-write, NOT transactional outbox
@Transactional
public CreateAccountResponseDto createAccount(CreateAccountRequestDto dto) {
    Account saved = accountRepository.save(new Account(...));

    // WRONG: Kafka is called INSIDE the @Transactional block
    // If Kafka succeeds but the outer transaction rolls back → account not persisted, event in Kafka
    // If Kafka is down → entire account creation fails with KafkaException
    kafkaTemplate.send("banco.account.created", event).get();

    return mapper.toDto(saved);
}
```

**Why this fails**:
- If the DB transaction rolls back after Kafka send succeeds → orphan event in Kafka with no matching domain record
- If Kafka is down → account creation fails even though that's a pure DB operation
- Tightly couples request latency to Kafka availability

**Fix**: Use transactional outbox — persist event to DB, let `OutboxScheduler` publish asynchronously.

---

### ❌ Common Mistake 2: Not Acknowledging on Duplicate (Creating Infinite Loop)

```java
// ❌ WRONG — failing to ack a duplicate causes infinite reprocessing
@KafkaListener(topics = "banco.transaction.completed", groupId = "fraud-detection")
public void onTransactionCompleted(String payload, Acknowledgment ack) {
    TransactionCompletedEvent event = objectMapper.readValue(payload, TransactionCompletedEvent.class);

    if (fraudDetectionService.alreadyProcessed(event.eventId())) {
        // WRONG: not calling ack.acknowledge() here
        // Consumer never advances the offset → same record re-consumed forever
        return;
    }

    fraudDetectionService.analyze(event);
    ack.acknowledge();
}
```

**Why this fails**:
- Without `ack.acknowledge()` on the duplicate path, the consumer group never advances past that offset
- Kafka broker keeps re-delivering the same record to the consumer
- Eventually fills the consumer's in-flight buffer, stalling all processing for that partition

**Fix**: Always call `ack.acknowledge()` on BOTH the duplicate path AND the success path.

---

### ❌ Common Mistake 3: Using @Autowired Instead of Constructor Injection in Kafka Beans

```java
// ❌ WRONG
@Component
public class OutboxScheduler {
    @Autowired  // field injection — untestable, hidden dependency
    private IOutboxRepository outboxRepository;

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;
}

// ✅ CORRECT
@Component
@RequiredArgsConstructor
public class OutboxScheduler {
    private final IOutboxRepository outboxRepository;      // injected via constructor by Lombok
    private final KafkaEventPublisher kafkaEventPublisher;
}
```

---

### ❌ Common Mistake 4: Using a Mutable Class for Event DTOs

```java
// ❌ WRONG — mutable DTO, Lombok @Data on an event payload
@Data
public class AccountCreatedEvent {
    private UUID eventId;
    private String eventType;
    // setters allow mutation → events should be immutable facts
}

// ✅ CORRECT — immutable Java Record
public record AccountCreatedEvent(
    UUID eventId,
    String eventType,
    int eventVersion,
    UUID aggregateId,
    Payload payload
) {}
```

---

## Spring Boot Configuration

### application.yml additions for Kafka

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
      properties:
        max.in.flight.requests.per.connection: 1
        enable.idempotence: true
        compression.type: snappy

    consumer:
      group-id: banco-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        isolation.level: read_committed
        max.poll.records: 500
        session.timeout.ms: 30000

    listener:
      ack-mode: manual
      concurrency: 3

    admin:
      auto-create: true   # set to false in production (topics pre-created)

# Outbox Scheduler Configuration
outbox:
  scheduler:
    enabled: true
    fixed-delay-ms: 1000        # poll every 1 second after previous batch completes
    initial-delay-ms: 5000      # wait 5 seconds after app startup before first poll
    batch-size: 100             # max events per scheduler cycle
    max-retry-count: 10         # events with retry_count >= this are skipped (stale)
```

### pom.xml dependencies to add

```xml
<!-- Spring Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <!-- version managed by spring-boot-starter-parent 4.0.2 -->
</dependency>

<!-- Testcontainers Kafka (integration tests only) -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

### Flyway migration (outbox table)

```sql
-- V4__create_account_events_outbox.sql
CREATE TABLE account_events_outbox (
    id             BINARY(16)   PRIMARY KEY,
    aggregate_id   BINARY(16)   NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    event_version  INT          NOT NULL DEFAULT 1,
    payload        JSON         NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500) NULL,

    INDEX idx_outbox_published_created (published, created_at),
    INDEX idx_outbox_aggregate (aggregate_id, aggregate_type)
);
```

---

## Examples for Banco-Service

### Registering a New Event Type (e.g., `card.blocked`)

1. Create the event record in `com.banco.co.card.event.CardBlockedEvent`
2. Add the topic mapping in `KafkaEventPublisher.TOPIC_ROUTING`:
   ```java
   "card.blocked", "banco.card.blocked"
   ```
3. Add the topic `@Bean` in `KafkaConfig`:
   ```java
   @Bean
   public NewTopic cardBlockedTopic() {
       return TopicBuilder.name("banco.card.blocked").partitions(3).replicas(1).build();
   }
   ```
4. In `CardService.blockCard()`, after saving the Card entity, persist to outbox:
   ```java
   OutboxEntity.of(card.getId(), "Card", "card.blocked", serialize(event))
   ```

No changes needed to `OutboxScheduler` — it is event-type agnostic.

### Monitoring Stale Events (Health Check)

```java
// Alert if events are permanently stuck (retryCount exceeded max)
long stale = outboxRepository.countStaleEntries(maxRetryCount);
if (stale > 0) {
    log.error("ALERT: {} events in outbox have exceeded max retries — manual intervention required", stale);
}
```

---

## Best Practices

| Rule | Reason |
|------|--------|
| Partition Kafka topics by `aggregateId` | Ordering guarantee: all Account-123 events arrive in order at the same partition |
| Always call `ack.acknowledge()` — even on duplicates | Not acking causes infinite reprocessing; ack duplicates and skip processing |
| Check `alreadyProcessed(eventId)` before processing | At-least-once delivery guarantees duplicates — consumers MUST be idempotent |
| Use `fixedDelay`, not `fixedRate` in scheduler | `fixedDelay` starts timer AFTER batch completes; `fixedRate` can overlap if Kafka is slow |
| Store payload as pre-serialized JSON string | Avoids double-serialization; `StringSerializer` on Kafka producer; Jackson in service layer |
| Set `ACKS_CONFIG = "all"` on producer | Ensures message durability across broker replicas |
| Set `MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = 1` | Preserves message ordering during retries |
| Do not call `kafkaTemplate.send()` inside `@Transactional` | Tight coupling to Kafka availability; use outbox pattern instead |
| Exclude Kafka events from global exception handler | `EventPublishingException` from publisher should not propagate to REST response |
| Topic naming: `banco.{feature}.{event-type}` | Consistent; easy to glob-subscribe (`banco.*`) for cross-cutting consumers |

---

## Resources

- Related: `spring-data-jpa-repositories.md` — `OutboxEntity` and `IOutboxRepository` JPA patterns
- Related: `java-exception-handling.md` — `EventPublishingException` extends `BankingException`
- Related: `java-records-dtos.md` — Event Records structure (immutable, nested Payload record)
- Related: `spring-boot-testing-junit5-complete.md` — Testing outbox with Testcontainers Kafka
- External: [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- SDD Design artifact: `sdd/kafka-event-driven-refactor/design` (engram, topic_key)
