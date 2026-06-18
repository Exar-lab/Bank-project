package com.banco.co.user.adapter.out.jpa;

import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.domain.port.out.IUserRepository;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED test for Task 1.1 — findSnapshotByUserId does not yet exist on IUserRepository.
 * Fails at compile time until the method is added to the port and implemented.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(classes = com.banco.co.BancoServiceApplication.class)
@Import(UserRepositorySnapshotTest.TestCryptoConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.test.database.replace=NONE"
})
@Transactional
class UserRepositorySnapshotTest {

    @TestConfiguration
    static class TestCryptoConfig {
        @Bean
        StringEncryptor stringEncryptor() {
            return new StringEncryptor() {
                @Override
                public String encrypt(String message) {
                    return message;
                }

                @Override
                public String decrypt(String encryptedMessage) {
                    return encryptedMessage;
                }
            };
        }
    }

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("banco_snapshot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IUserJpaRepository userJpaRepository;

    private UserEntity savedUser;

    @BeforeEach
    void setUp() {
        savedUser = buildSampleUser("snapshot-test@banco.co", "1122334455");
        savedUser = userJpaRepository.saveAndFlush(savedUser);
    }

    @Test
    void testFindSnapshotByUserId_ExistingUser_ReturnsSnapshot() {
        UUID userId = savedUser.getId();

        // RED: this call will not compile until findSnapshotByUserId is added to IUserRepository
        UserSnapshot snapshot = userRepository.findSnapshotByUserId(userId);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.userId()).isEqualTo(userId.toString());
        assertThat(snapshot.email()).isEqualTo("snapshot-test@banco.co");
        assertThat(snapshot.username()).isNotNull();
    }

    private UserEntity buildSampleUser(String email, String documentNumber) {
        UserEntity user = new UserEntity();
        user.setUserCode("USR-SNAPSHOT");
        user.setFistName("Snapshot");
        user.setLastName("Test");
        user.setUsername("snapshot-test");
        user.setEmail(email);
        user.setDocumentType(DocumentType.CEDULA);
        user.setDocumentNumber(documentNumber);
        user.setPhoneNumber("+57300129876");
        user.setAddress("Test Address 456");
        user.setBirthDate(LocalDate.of(1985, 6, 15));
        user.setStatus(UserStatus.ACTIVE);
        user.setKycStatus(KycStatus.PENDING);
        return user;
    }
}
