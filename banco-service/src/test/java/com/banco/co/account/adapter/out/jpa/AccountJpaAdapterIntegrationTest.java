package com.banco.co.account.adapter.out.jpa;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.model.User;
import com.banco.co.user.repository.IUserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for AccountJpaAdapter.
 * Uses Testcontainers MySQL to verify real CRUD operations.
 * Naming: test{Scenario}_{Expected}
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(classes = com.banco.co.BancoServiceApplication.class)
@Import(AccountJpaAdapterIntegrationTest.TestCryptoConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.test.database.replace=NONE"
})
@Transactional
class AccountJpaAdapterIntegrationTest {

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
            .withDatabaseName("banco_account_adapter_test")
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
    private AccountJpaAdapter adapter;

    // Legacy JPA user repository — used to set up the required User foreign key
    @Autowired
    private IUserRepository legacyUserRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = buildSampleUser("account-adapter@banco.co", "9988776655");
        savedUser = legacyUserRepository.save(savedUser);
    }

    // ══════════════════════════════════════════════════════════
    //  T13 — AccountJpaAdapter integration tests
    // ══════════════════════════════════════════════════════════

    @Test
    void testSave_WhenValidAccount_PersistsAndReturnsDomainAccount() {
        Account account = buildSampleAccount(savedUser);

        Account saved = adapter.save(account);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAccountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getBalance()).isEqualByComparingTo("0.00");
        assertThat(saved.getUserId()).isEqualTo(savedUser.getId());
    }

    @Test
    void testFindById_WhenAccountExists_ReturnsDomainAccount() {
        Account account = buildSampleAccount(savedUser);
        Account saved = adapter.save(account);

        Optional<Account> result = adapter.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
        assertThat(result.get().getAccountType()).isEqualTo(AccountType.SAVINGS);
    }

    @Test
    void testFindById_WhenAccountDoesNotExist_ReturnsEmpty() {
        Optional<Account> result = adapter.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void testFindAllByUserId_WhenUserHasMultipleAccounts_ReturnsAll() {
        Account savings = buildSampleAccount(savedUser);
        savings.setAccountType(AccountType.SAVINGS);
        savings.generateAccountCode();
        Account saved1 = adapter.save(savings);

        Account checking = buildSampleAccount(savedUser);
        checking.setAccountType(AccountType.CHECKING);
        checking.generateAccountCode();
        Account saved2 = adapter.save(checking);

        List<Account> results = adapter.findAllByUserId(savedUser.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Account::getId)
                .containsExactlyInAnyOrder(saved1.getId(), saved2.getId());
    }

    @Test
    void testFindAllByUserId_WhenUserHasNoAccounts_ReturnsEmptyList() {
        List<Account> results = adapter.findAllByUserId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    @Test
    void testFindActiveById_WhenAccountIsActive_ReturnsDomainAccount() {
        Account account = buildSampleAccount(savedUser);
        Account saved = adapter.save(account);

        Optional<Account> result = adapter.findActiveById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void testFindByAccountCode_WhenAccountExists_ReturnsDomainAccount() {
        Account account = buildSampleAccount(savedUser);
        Account saved = adapter.save(account);

        Optional<Account> result = adapter.findByAccountCode(saved.getAccountCode());

        assertThat(result).isPresent();
        assertThat(result.get().getAccountCode()).isEqualTo(saved.getAccountCode());
        assertThat(result.get().getUserId()).isEqualTo(savedUser.getId());
    }

    @Test
    void testFindByAccountCode_WhenAccountDoesNotExist_ReturnsEmpty() {
        Optional<Account> result = adapter.findByAccountCode("NONEXISTENT-CODE");

        assertThat(result).isEmpty();
    }

    // ══════════════════════════════════════════════════════════
    //  Helper methods
    // ══════════════════════════════════════════════════════════

    private Account buildSampleAccount(User user) {
        Account account = new Account();
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);
        account.setBlockedBalance(BigDecimal.ZERO);
        account.setOverdraftLimit(BigDecimal.ZERO);
        account.setMoneyFromEnvelope(BigDecimal.ZERO);
        account.setCurrency("CRC");
        account.setMaxEnvelope(10);
        account.setUserId(user.getId());
        account.generateAccountCode();
        return account;
    }

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
