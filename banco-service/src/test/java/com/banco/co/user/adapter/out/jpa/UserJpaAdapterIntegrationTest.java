package com.banco.co.user.adapter.out.jpa;

import com.banco.co.user.domain.model.User;
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(classes = com.banco.co.BancoServiceApplication.class)
@Import(UserJpaAdapterIntegrationTest.TestCryptoConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.test.database.replace=NONE"
})
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserJpaAdapterIntegrationTest {

    private final UserJpaAdapter adapter;

    UserJpaAdapterIntegrationTest(UserJpaAdapter adapter) {
        this.adapter = adapter;
    }

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
            .withDatabaseName("banco_adapter_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    // ══════════════════════════════════════════════════════════
    //  T7 — UserJpaAdapter integration tests
    //  Naming: test{Scenario}_{Expected}
    // ══════════════════════════════════════════════════════════

    @Test
    void testSave_WhenValidUser_PersistsAndReturnsDomainUser() {
        User user = buildSampleUser("save@banco.co", "1234567890");

        User saved = adapter.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("save@banco.co");
        assertThat(saved.getFistName()).isEqualTo("Test");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void testFindByEmail_WhenUserExists_ReturnsDomainUser() {
        User user = buildSampleUser("findme@banco.co", "9876543210");
        adapter.save(user);

        Optional<User> result = adapter.findByEmail("findme@banco.co");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("findme@banco.co");
        assertThat(result.get().getFistName()).isEqualTo("Test");
    }

    @Test
    void testFindByEmail_WhenUserDoesNotExist_ReturnsEmpty() {
        Optional<User> result = adapter.findByEmail("nonexistent@banco.co");

        assertThat(result).isEmpty();
    }

    @Test
    void testFindActiveByEmail_WhenUserIsActive_ReturnsDomainUser() {
        User user = buildSampleUser("active@banco.co", "1111111111");
        user.setStatus(UserStatus.ACTIVE);
        adapter.save(user);

        Optional<User> result = adapter.findActiveByEmail("active@banco.co");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void testFindActiveByEmail_WhenUserIsSuspended_ReturnsEmpty() {
        User user = buildSampleUser("suspended@banco.co", "2222222222");
        User saved = adapter.save(user);
        saved.setStatus(UserStatus.SUSPENDED);
        adapter.save(saved);

        Optional<User> result = adapter.findActiveByEmail("suspended@banco.co");

        assertThat(result).isEmpty();
    }

    @Test
    void testExistsByEmail_WhenUserExists_ReturnsTrue() {
        User user = buildSampleUser("exists@banco.co", "3333333333");
        adapter.save(user);

        boolean exists = adapter.existsByEmail("exists@banco.co");

        assertThat(exists).isTrue();
    }

    @Test
    void testExistsByEmail_WhenUserDoesNotExist_ReturnsFalse() {
        boolean exists = adapter.existsByEmail("nobody@banco.co");

        assertThat(exists).isFalse();
    }

    // ══════════════════════════════════════════════════════════
    //  Helper
    // ══════════════════════════════════════════════════════════

    private User buildSampleUser(String email, String documentNumber) {
        User user = new User();
        user.setFistName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setDocumentType(DocumentType.CEDULA);
        user.setDocumentNumber(documentNumber);
        user.setPhoneNumber("+5730012" + documentNumber.substring(0, 5));
        user.setAddress("Test Address 123");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setStatus(UserStatus.ACTIVE);
        user.setKycStatus(KycStatus.PENDING);
        user.generateData();
        return user;
    }
}
