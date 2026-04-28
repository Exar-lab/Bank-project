package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.repository.EmailOutboxEventJpaRepository;
import com.banco.co.notification.email.repository.EmailOutboxRepositoryAdapter;
import com.banco.co.notification.email.service.EmailAuditPublisher;
import com.banco.co.notification.email.service.EmailServiceImpl;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        classes = {
                TransactionEventsEmailHandler.class,
                EmailServiceImpl.class,
                EmailOutboxRepositoryAdapter.class,
                TransactionEventsEmailHandlerIdempotencyTest.TestConfig.class
        },
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration",
                "spring.kafka.listener.auto-startup=false"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class TransactionEventsEmailHandlerIdempotencyTest {

    @TestConfiguration
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackages = "com.banco.co.notification.email.repository")
    static class TestConfig implements BeanFactoryAware {

        private ConfigurableListableBeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EmailAuditPublisher emailAuditPublisher() {
            return mock(EmailAuditPublisher.class);
        }

        @Bean
        DataSource dataSource(
                @Value("${spring.datasource.url}") String url,
                @Value("${spring.datasource.username}") String username,
                @Value("${spring.datasource.password}") String password,
                @Value("${spring.datasource.driver-class-name}") String driverClassName
        ) {
            return DataSourceBuilder.create()
                    .url(url).username(username).password(password).driverClassName(driverClassName)
                    .build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan("com.banco.co.notification.email.model");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            vendorAdapter.setGenerateDdl(true);
            emf.setJpaVendorAdapter(vendorAdapter);
            Properties jpaProperties = new Properties();
            jpaProperties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            jpaProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            jpaProperties.setProperty("hibernate.show_sql", "false");
            jpaProperties.put("hibernate.resource.beans.container", new SpringBeanContainer(this.beanFactory));
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
            .withDatabaseName("banco_idempotency_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("jasypt.encryptor.password", () -> "test-encryptor-password");
        registry.add("security.jwt.secret-key", () -> "test-secret-key-for-integration-tests-minimum-256-bits-ok");
        registry.add("security.jwt.issuer", () -> "test-issuer");
    }

    @Autowired
    private TransactionEventsEmailHandler handler;

    @Autowired
    private EmailOutboxEventJpaRepository emailOutboxEventJpaRepository;

    @BeforeEach
    void setUp() {
        emailOutboxEventJpaRepository.deleteAll();
    }

    @Test
    void testIdempotency_SamePayloadTwice_ProducesOneRowPerEventId() {
        String txId = UUID.randomUUID().toString();
        String payload = buildTransferPayload(txId);

        assertThatNoException().isThrownBy(() -> handler.consume(payload));
        assertThatNoException().isThrownBy(() -> handler.consume(payload));

        assertThat(emailOutboxEventJpaRepository.countByEventId(txId + ":sender")).isEqualTo(1);
        assertThat(emailOutboxEventJpaRepository.countByEventId(txId + ":receiver")).isEqualTo(1);
        assertThat(emailOutboxEventJpaRepository.count()).isEqualTo(2);
    }

    private String buildTransferPayload(String txId) {
        return """
                {
                  "eventType": "TransactionCompletedNotification",
                  "transactionId": "%s",
                  "transactionCode": "TXN-BCR-2024-X7K9P2M3",
                  "type": "TRANSFER",
                  "amount": 5000.00,
                  "currency": "CRC",
                  "occurredAt": "2026-04-24T10:00:00",
                  "fromAccount": {
                    "accountCode": "AC-BCR-0001",
                    "userId": "11111111-1111-1111-1111-111111111111",
                    "userEmail": "alice@banco.co",
                    "userFirstName": "Alice"
                  },
                  "toAccount": {
                    "accountCode": "AC-BCR-0002",
                    "userId": "22222222-2222-2222-2222-222222222222",
                    "userEmail": "bob@banco.co",
                    "userFirstName": "Bob"
                  }
                }
                """.formatted(txId);
    }
}
