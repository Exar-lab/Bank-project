package com.banco.co.notification.email.adapter.in;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLog;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.repository.IAuditLogRepository;
import com.banco.co.auditLog.service.AuditLogProcessor;
import com.banco.co.auditLog.service.AuditLogService;
import com.banco.co.notification.email.adapter.JavaMailEmailDispatcher;
import com.banco.co.notification.email.adapter.ThymeleafEmailTemplateRenderer;
import com.banco.co.notification.email.config.MailConfig;
import com.banco.co.notification.email.config.MailExecutorConfig;
import com.banco.co.notification.email.config.MailProperties;
import com.banco.co.notification.email.config.ThymeleafEmailConfig;
import com.banco.co.notification.email.model.EmailOutboxEvent;
import com.banco.co.notification.email.model.EmailOutboxStatus;
import com.banco.co.notification.email.relay.EmailOutboxDispatchWorker;
import com.banco.co.notification.email.relay.EmailOutboxRelay;
import com.banco.co.notification.email.repository.EmailOutboxEventJpaRepository;
import com.banco.co.notification.email.repository.EmailOutboxRepositoryAdapter;
import com.banco.co.notification.email.service.EmailAuditPublisher;
import com.banco.co.notification.email.service.EmailServiceImpl;
import com.banco.co.notification.email.service.RecipientHasher;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.model.User;
import com.banco.co.user.repository.IUserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                // Email relay components
                UserEventsConsumer.class,
                EmailOutboxRelay.class,
                EmailOutboxDispatchWorker.class,
                EmailOutboxRepositoryAdapter.class,
                // Email service
                EmailServiceImpl.class,
                EmailAuditPublisher.class,
                RecipientHasher.class,
                // Email infrastructure
                JavaMailEmailDispatcher.class,
                ThymeleafEmailTemplateRenderer.class,
                // Email configs
                MailConfig.class,
                MailExecutorConfig.class,
                ThymeleafEmailConfig.class,
                // Audit log
                AuditLogService.class,
                AuditLogProcessor.class,
                // Test config (ObjectMapper, @EnableJpaAuditing, @EnableAsync, @EnableConfigurationProperties)
                EmailRelayIntegrationCorrectiveTest.TestConfig.class
        },
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration",
                "spring.kafka.listener.auto-startup=false",
                "spring.task.scheduling.enabled=false"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class EmailRelayIntegrationCorrectiveTest {

    @TestConfiguration
    @EnableJpaAuditing
    @EnableAsync
    @EnableConfigurationProperties(MailProperties.class)
    @EnableJpaRepositories(basePackages = "com.banco.co")
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        StringEncryptor stringEncryptor() {
            return new StringEncryptor() {
                @Override public String encrypt(String message) { return message; }
                @Override public String decrypt(String encryptedMessage) { return encryptedMessage; }
            };
        }

        @Bean
        DataSource dataSource(
                @Value("${spring.datasource.url}") String url,
                @Value("${spring.datasource.username}") String username,
                @Value("${spring.datasource.password}") String password,
                @Value("${spring.datasource.driver-class-name}") String driverClassName
        ) {
            return DataSourceBuilder.create()
                    .url(url)
                    .username(username)
                    .password(password)
                    .driverClassName(driverClassName)
                    .build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan(
                    "com.banco.co.account.model",
                    "com.banco.co.auditLog.model",
                    "com.banco.co.card.model",
                    "com.banco.co.envelope.model",
                    "com.banco.co.fraud.riskprofile.model",
                    "com.banco.co.notification.email.model",
                    "com.banco.co.outbox.model",
                    "com.banco.co.permission.model",
                    "com.banco.co.role.model",
                    "com.banco.co.security.token.model",
                    "com.banco.co.transaction.model",
                    "com.banco.co.user.model"
            );
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            vendorAdapter.setGenerateDdl(true);
            emf.setJpaVendorAdapter(vendorAdapter);
            Properties jpaProperties = new Properties();
            jpaProperties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            jpaProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            jpaProperties.setProperty("hibernate.show_sql", "false");
            emf.setJpaProperties(jpaProperties);
            return emf;
        }

        @Bean
        JpaTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
            JpaTransactionManager txManager = new JpaTransactionManager();
            txManager.setEntityManagerFactory(entityManagerFactory.getObject());
            return txManager;
        }
    }

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("banco_email_relay_test")
            .withUsername("test")
            .withPassword("test");

    private static final int GREENMAIL_PORT = 3025;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("banco.mail.enabled", () -> "true");
        registry.add("banco.mail.host", () -> "localhost");
        registry.add("banco.mail.port", () -> String.valueOf(GREENMAIL_PORT));
        registry.add("banco.mail.username", () -> "");
        registry.add("banco.mail.password", () -> "");
        registry.add("banco.mail.from", () -> "test-no-reply@banco.co");
        registry.add("banco.mail.relay.batch-size", () -> "10");
        registry.add("banco.mail.relay.poll-delay-ms", () -> "100");
        registry.add("banco.mail.relay.max-attempts", () -> "3");
        registry.add("banco.mail.executor.core-pool-size", () -> "1");
        registry.add("banco.mail.executor.max-pool-size", () -> "2");
        registry.add("banco.mail.executor.queue-capacity", () -> "20");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("security.jwt.secret-key", () -> "test-secret-key-for-integration-tests-minimum-256-bits-ok");
        registry.add("security.jwt.issuer", () -> "test-issuer");
        registry.add("jasypt.encryptor.password", () -> "test-encryptor-password");
    }

    @Autowired
    private UserEventsConsumer userEventsConsumer;

    @Autowired
    private EmailOutboxRelay emailOutboxRelay;

    @Autowired
    private EmailOutboxDispatchWorker dispatchWorker;

    @Autowired
    private EmailOutboxEventJpaRepository emailOutboxEventJpaRepository;

    @Autowired
    private IAuditLogRepository auditLogRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private GreenMail greenMail;

    @BeforeEach
    void setUp() {
        ServerSetup smtp = new ServerSetup(GREENMAIL_PORT, "127.0.0.1", "smtp");
        smtp.setServerStartupTimeout(20_000);
        greenMail = new GreenMail(smtp);
        greenMail.start();
        emailOutboxEventJpaRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    void testRetryLifecycle_WhenDispatchRepeatedlyFails_ThenRowEndsDead() {
        User user = createUser("retry-user@banco.co");
        EmailOutboxEvent event = createOutboxEvent(
                "evt-retry-dead-001",
                user.getId(),
                user.getEmail(),
                user.getFistName(),
                "email/nonexistent",
                Map.of("recipientName", user.getFistName(), "userCode", "USR-RETRY", "bankName", "Banco CO"),
                "Asunto retry"
        );

        emailOutboxEventJpaRepository.save(event);

        emailOutboxRelay.relayPendingEmails();
        awaitStatus("evt-retry-dead-001", EmailOutboxStatus.PENDING, 5);
        EmailOutboxEvent firstAttempt = findByEventIdOrThrow("evt-retry-dead-001");
        assertThat(firstAttempt.getAttemptCount()).isEqualTo(1);
        assertThat(firstAttempt.getAvailableAt()).isAfter(LocalDateTime.now().minusSeconds(5));

        unlockForRetry("evt-retry-dead-001");
        emailOutboxRelay.relayPendingEmails();
        awaitStatus("evt-retry-dead-001", EmailOutboxStatus.PENDING, 5);
        EmailOutboxEvent secondAttempt = findByEventIdOrThrow("evt-retry-dead-001");
        assertThat(secondAttempt.getAttemptCount()).isEqualTo(2);

        unlockForRetry("evt-retry-dead-001");
        emailOutboxRelay.relayPendingEmails();
        awaitStatus("evt-retry-dead-001", EmailOutboxStatus.DEAD, 5);

        EmailOutboxEvent dead = findByEventIdOrThrow("evt-retry-dead-001");
        assertThat(dead.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(dead.getAttemptCount()).isEqualTo(3);

        List<AuditLog> deadAudits = auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction() == AuditAction.EMAIL_DELIVERY_DEAD)
                .toList();
        assertThat(deadAudits).isNotEmpty();
        AuditLog audit = deadAudits.getFirst();
        assertThat(audit.getDetails()).extracting(AuditLogDetail::getKey)
                .contains("eventId", "attemptCount", "templateName");
    }

    @Test
    void testDuplicateEventE2E_WhenSameEventConsumedTwice_ThenNoDuplicateRowOrEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(new HashMap<>(Map.of(
                "eventId", "evt-dedup-001",
                "eventType", "UserCreated",
                "userId", userId.toString(),
                "email", "dedup-user@banco.co",
                "recipientName", "Dedup User",
                "userCode", "USR-DEDUP"
        )));

        userEventsConsumer.consume(payload);
        userEventsConsumer.consume(payload);

        assertThat(emailOutboxEventJpaRepository.countByEventId("evt-dedup-001")).isEqualTo(1);

        emailOutboxRelay.relayPendingEmails();
        awaitStatus("evt-dedup-001", EmailOutboxStatus.SENT, 5);

        Message[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);

        List<AuditLog> dedupeAudits = auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction() == AuditAction.EMAIL_ENQUEUE_DEDUPED)
                .toList();
        assertThat(dedupeAudits).hasSize(1);
    }

    @Test
    void testMultiInstanceContention_WhenTwoWorkersDispatchSameProcessingRow_ThenOnlyOneSendOccurs() {
        User user = createUser("contention-user@banco.co");
        EmailOutboxEvent event = createOutboxEvent(
                "evt-contention-001",
                user.getId(),
                user.getEmail(),
                user.getFistName(),
                "email/welcome",
                Map.of("recipientName", user.getFistName(), "userCode", "USR-CONT", "bankName", "Banco CO"),
                "Asunto contention"
        );
        event.setStatus(EmailOutboxStatus.PROCESSING);
        event.setClaimedBy("relay-A");
        emailOutboxEventJpaRepository.save(event);

        Long eventId = findByEventIdOrThrow("evt-contention-001").getId();

        Thread t1 = new Thread(() -> dispatchWorker.dispatchSafely(eventId));
        Thread t2 = new Thread(() -> dispatchWorker.dispatchSafely(eventId));
        t1.start();
        t2.start();
        joinThread(t1);
        joinThread(t2);

        awaitStatus("evt-contention-001", EmailOutboxStatus.SENT, 5);

        Message[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);

        List<AuditLog> sentAudits = auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction() == AuditAction.EMAIL_SENT)
                .toList();
        assertThat(sentAudits).hasSize(1);
    }

    @Test
    void testAuditContract_WhenDispatchSucceeds_ThenLogsUserEntityAndRecipientHashOnly() {
        User user = createUser("audit-user@banco.co");
        EmailOutboxEvent event = createOutboxEvent(
                "evt-audit-001",
                user.getId(),
                user.getEmail(),
                user.getFistName(),
                "email/welcome",
                Map.of("recipientName", user.getFistName(), "userCode", "USR-AUDIT", "bankName", "Banco CO"),
                "Asunto audit"
        );
        emailOutboxEventJpaRepository.save(event);

        emailOutboxRelay.relayPendingEmails();
        awaitStatus("evt-audit-001", EmailOutboxStatus.SENT, 5);

        List<AuditLog> sentAudits = auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction() == AuditAction.EMAIL_SENT)
                .toList();
        assertThat(sentAudits).isNotEmpty();

        AuditLog audit = sentAudits.getFirst();
        assertThat(audit.getEntityType()).isEqualTo(AuditEntityType.USER);
        assertThat(audit.getEntityId()).isEqualTo(user.getId().toString());
        assertThat(audit.getDetails()).hasSize(1);

        AuditLogDetail detail = audit.getDetails().getFirst();
        assertThat(detail.getKey()).isEqualTo("recipientHash");
        assertThat(detail.getValue()).isEqualTo(sha256(user.getEmail()));
        assertThat(detail.getValue()).isNotEqualTo(user.getEmail());
    }

    private User createUser(String email) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        user.setFistName("Name" + suffix.substring(0, 4));
        user.setLastName("Last" + suffix.substring(4));
        user.setEmail(email);
        user.setDocumentType(DocumentType.CEDULA);
        user.setDocumentNumber("30" + suffix.substring(0, 6));
        user.setPhoneNumber("+57300" + suffix.substring(0, 6));
        user.setAddress("Address " + suffix);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        return userRepository.save(user);
    }

    private EmailOutboxEvent createOutboxEvent(
            String eventId,
            UUID userId,
            String recipientEmail,
            String recipientName,
            String templateName,
            Map<String, Object> context,
            String subject
    ) {
        try {
            String contextJson = objectMapper.writeValueAsString(context);
            return new EmailOutboxEvent(eventId, userId, recipientEmail, recipientName, templateName, contextJson, subject);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize context", ex);
        }
    }

    private EmailOutboxEvent findByEventIdOrThrow(String eventId) {
        Optional<EmailOutboxEvent> event = emailOutboxEventJpaRepository.findByEventId(eventId);
        return event.orElseThrow(() -> new AssertionError("Outbox event not found: " + eventId));
    }

    private void unlockForRetry(String eventId) {
        EmailOutboxEvent event = findByEventIdOrThrow(eventId);
        event.setAvailableAt(LocalDateTime.now().minusSeconds(1));
        event.setClaimedBy(null);
        event.setStatus(EmailOutboxStatus.PENDING);
        emailOutboxEventJpaRepository.save(event);
    }

    private void awaitStatus(String eventId, EmailOutboxStatus expected, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
        while (System.currentTimeMillis() < deadline) {
            EmailOutboxEvent current = findByEventIdOrThrow(eventId);
            if (current.getStatus() == expected) {
                return;
            }
            sleepMillis(100);
        }
        EmailOutboxEvent last = findByEventIdOrThrow(eventId);
        throw new AssertionError("Expected status " + expected + " but was " + last.getStatus());
    }

    private void joinThread(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for worker", ex);
        }
        assertTrue(!thread.isAlive(), "Worker thread did not finish in time");
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash", ex);
        }
    }
}
