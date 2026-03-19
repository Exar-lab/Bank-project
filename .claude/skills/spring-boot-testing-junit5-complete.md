---
name: spring-boot-testing-junit5-complete
description: "Complete JUnit 5 testing guide: @DataJpaTest, @WebMvcTest, @SpringBootTest, MockMvc, TestContainers"
trigger: "When writing unit tests, integration tests, or repository tests for Spring Boot"
layer: all
tags:
  - testing
  - junit5
  - mockito
  - testcontainers
  - coverage
---

# Skill: spring-boot-testing-junit5-complete

## When to Use

**Trigger Context**:
- Testing JPA repositories (database layer)
- Testing Spring MVC controllers (REST endpoints)
- Testing services with mocked dependencies
- Testing with real database (integration tests)
- Testing security (authenticated endpoints)

**Coverage Targets** (banco-service conventions):
- **Domain Layer**: 90%+ (entities, enums, exceptions)
- **Application Layer**: 85%+ (services, mappers, use cases)
- **Infrastructure Layer**: 70%+ (repositories, configs)

**Decision Tree**:
- Testing repository queries? → Use `@DataJpaTest` (fast, in-memory H2)
- Testing REST endpoint? → Use `@WebMvcTest` (mock service layer)
- Testing service logic? → Use `@SpringBootTest` or manual mock setup
- Testing with real database? → Use `TestContainers` + MySQL
- Testing security? → Use `@WebMvcTest` + `@WithMockUser`

---

## Critical Patterns

### ✅ Correct: Repository Test with @DataJpaTest

```java
package com.banco.co.account.repository;

import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.model.Account;
import com.banco.co.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest  // Only loads JPA/repository components, not full Spring context
@DisplayName("Account Repository Tests")
class AccountRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;  // Manage test data
    
    @Autowired
    private IAccountRepository repository;
    
    private Account testAccount;
    private User testUser;
    
    @BeforeEach
    void setup() {
        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setDocumentNumber("123456789");
        entityManager.persistAndFlush(testUser);
        
        // Create test account
        testAccount = new Account();
        testAccount.setUser(testUser);
        testAccount.setAccountCode("ACC001");
        testAccount.setStatus(AccountStatus.ACTIVE);
        entityManager.persistAndFlush(testAccount);
    }
    
    @Test
    @DisplayName("Should find active account by email")
    void testFindActiveAccountByEmail() {
        // When
        Optional<Account> result = repository.findFirstActiveAccountByUser_Email("test@example.com");
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("ACC001", result.get().getAccountCode());
    }
    
    @Test
    @DisplayName("Should return empty when account not found")
    void testFindAccountNotFound() {
        // When
        Optional<Account> result = repository.findFirstActiveAccountByUser_Email("notfound@example.com");
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("Should fetch account with user eagerly (no N+1)")
    void testFindAccountWithUserEagerLoad() {
        // When
        Optional<Account> result = repository.findAccountWithUser("ACC001");
        
        // Then
        assertTrue(result.isPresent());
        Account account = result.get();
        assertNotNull(account.getUser());  // User is loaded
        assertEquals("test@example.com", account.getUser().getEmail());
    }
}
```

**Why this works**:
- `@DataJpaTest` boots only JPA layer (fast: ~500ms per test)
- `TestEntityManager` persists test data and manages transactions
- `@DisplayName` makes test names human-readable in reports
- Setup creates consistent test data via `@BeforeEach`
- `Optional` assertions prevent null pointer exceptions
- Tests verify both happy path and error cases

---

### ✅ Correct: Service Test with Mocked Repository

```java
package com.banco.co.account.service;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.mapper.IAccountMapper;
import com.banco.co.account.model.Account;
import com.banco.co.account.repository.IAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)  // Enable Mockito mocking
class AccountServiceTest {
    
    @Mock
    private IAccountRepository accountRepository;
    
    @Mock
    private IAccountMapper accountMapper;
    
    @InjectMocks  // Inject mocks into service
    private AccountService accountService;
    
    private Account mockAccount;
    private AccountResponseDto mockDto;
    
    @BeforeEach
    void setup() {
        mockAccount = new Account();
        mockAccount.setId(UUID.randomUUID());
        mockAccount.setAccountCode("ACC001");
        
        mockDto = new AccountResponseDto(/* ... */);
    }
    
    @Test
    void testGetAccountSuccess() {
        // Given
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(mockAccount));
        when(accountMapper.toDto(mockAccount))
            .thenReturn(mockDto);
        
        // When
        AccountResponseDto result = accountService.getAccount(accountId);
        
        // Then
        assertNotNull(result);
        assertEquals("ACC001", result.accountCode());
        verify(accountRepository).findById(accountId);  // Verify interaction
        verify(accountMapper).toDto(mockAccount);
    }
    
    @Test
    void testGetAccountNotFound() {
        // Given
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());
        
        // When/Then
        assertThrows(AccountNotFoundException.class, 
            () -> accountService.getAccount(accountId));
        verify(accountRepository).findById(accountId);
        verifyNoInteractions(accountMapper);  // Mapper not called
    }
}
```

**Why this works**:
- `@ExtendWith(MockitoExtension.class)` provides mock setup
- `@Mock` creates mock objects (not real implementations)
- `@InjectMocks` auto-injects mocks into service
- `when(...).thenReturn(...)` defines mock behavior
- `verify(...)` confirms interactions (service calls repo)
- Tests are isolated: no database, no Spring context

---

### ✅ Correct: Controller Test with MockMvc

```java
package com.banco.co.account.controller;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)  // Only load controller, mock service
class AccountControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;  // JSON serialization
    
    @MockBean  // Spring context mock
    private AccountService accountService;
    
    private AccountResponseDto mockDto;
    
    @BeforeEach
    void setup() {
        mockDto = new AccountResponseDto(
            UUID.randomUUID(),
            "ACC001",
            "test@example.com"
        );
    }
    
    @Test
    @WithMockUser  // Authenticate as mock user
    void testGetAccountSuccess() throws Exception {
        // Given
        UUID accountId = UUID.randomUUID();
        when(accountService.getAccount(accountId))
            .thenReturn(mockDto);
        
        // When/Then
        mockMvc.perform(get("/api/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountCode").value("ACC001"))
            .andExpect(jsonPath("$.userEmail").value("test@example.com"));
    }
    
    @Test
    void testGetAccountUnauthorized() throws Exception {
        // When/Then - No @WithMockUser, so request is unauthorized
        mockMvc.perform(get("/api/accounts/123"))
            .andExpect(status().isUnauthorized());  // 401
    }
}
```

**Why this works**:
- `@WebMvcTest` loads only controller and web layer (~100ms)
- `MockMvc` simulates HTTP requests without actual server
- `@MockBean` creates mocks managed by Spring context
- `@WithMockUser` authenticates request with test credentials
- `perform().andExpect()` verifies HTTP status and response
- `jsonPath("$.field")` extracts JSON fields from response

---

### ✅ Correct: Integration Test with Real Database (TestContainers)

```java
package com.banco.co.account.integration;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.model.Account;
import com.banco.co.account.repository.IAccountRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest  // Full Spring context with real database
@Testcontainers  // Enable TestContainers
class AccountIntegrationTest {
    
    @Container
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("banco_test")
        .withUsername("root")
        .withPassword("test123");
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
    }
    
    @Autowired
    private IUserRepository userRepository;
    
    @Autowired
    private IAccountRepository accountRepository;
    
    @Autowired
    private AccountService accountService;
    
    @BeforeEach
    void setup() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }
    
    @Test
    void testCreateAccountEnd2End() {
        // Given
        User user = new User();
        user.setEmail("test@example.com");
        user.setDocumentNumber("123456");
        userRepository.save(user);
        
        // When
        AccountResponseDto response = accountService.createAccount(/* ... */);
        
        // Then - Real database contains data
        Account saved = accountRepository.findFirstActiveAccountByUser_Email("test@example.com")
            .orElseThrow();
        assertEquals(saved.getId(), response.id());
    }
}
```

**Why this works**:
- `@Testcontainers` spins up real MySQL container (not in-memory)
- Full Spring Boot context: all beans, AOP, transactions enabled
- Tests actual database behavior (JPA, constraints, relationships)
- `@DynamicPropertySource` injects container credentials into application.yml
- Slower (~5-10s per test) but validates real scenarios
- Best for critical integration flows

---

### ❌ Wrong: Testing Without @Transactional Rollback

```java
// WRONG: Test data not cleaned up between tests
@DataJpaTest
class AccountRepositoryTest {
    
    @Autowired
    private IAccountRepository repository;
    
    @Test
    void testCreateAccount1() {
        Account account = new Account();
        account.setAccountCode("ACC001");
        repository.save(account);
        // Test data NOT rolled back
    }
    
    @Test
    void testCreateAccount2() {
        Account account = new Account();
        account.setAccountCode("ACC001");
        repository.save(account);  // Duplicate key constraint violation!
        // TEST FAILS due to test pollution
    }
}
```

**Why this fails**:
- Without rollback, test1 commits ACC001 to database
- test2 tries to insert same ACC001 → constraint violation
- Tests pass individually but fail together (test pollution)
- Unpredictable test execution order

**Fix**: `@DataJpaTest` auto-enables `@Transactional` with rollback (no action needed)

---

### ❌ Wrong: Not Mocking External Service Calls

```java
// WRONG: Service test hits REAL external API
@SpringBootTest
class PaymentServiceTest {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private ExternalPaymentGateway paymentGateway;  // Real bean!
    
    @Test
    void testProcessPayment() {
        // When
        paymentService.processPayment(UUID.randomUUID(), BigDecimal.TEN);
        
        // PROBLEM: Test calls REAL external payment API
        // - Slow (5+ seconds)
        // - Network dependency (fails if API down)
        // - Side effects (actual charges!)
    }
}
```

**Why this fails**:
- Tests depend on external service availability
- Slow: network latency (real requests take seconds)
- Unpredictable: external service might be down
- Dangerous: might charge real credit cards!
- Cannot test error scenarios (API timeout, 500 error)

**Fix**: Mock external dependencies:
```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    @Mock
    private ExternalPaymentGateway paymentGateway;  // MOCK, not real
    
    @InjectMocks
    private PaymentService paymentService;
    
    @Test
    void testProcessPaymentSuccess() {
        // Given
        when(paymentGateway.charge(any()))
            .thenReturn(PaymentResponse.success("TXN123"));
        
        // When
        paymentService.processPayment(UUID.randomUUID(), BigDecimal.TEN);
        
        // Then - Fast, no network, no side effects
        verify(paymentGateway).charge(any());
    }
    
    @Test
    void testProcessPaymentFails() {
        // Given - External gateway fails
        when(paymentGateway.charge(any()))
            .thenThrow(new PaymentException("Connection timeout"));
        
        // When/Then - Test failure scenario
        assertThrows(PaymentException.class, 
            () -> paymentService.processPayment(UUID.randomUUID(), BigDecimal.TEN));
    }
}
```

---

### ✅ Correct: Reliable Assertions for Temporal Data

```java
@DataJpaTest
class AuditLogRepositoryTest {
    
    @Autowired
    private IAccountRepository accountRepository;
    
    @Test
    void testAccountCreatedAtTimestamp() {
        // Given
        Account account = new Account();
        account.setAccountCode("ACC001");
        
        LocalDateTime beforeSave = LocalDateTime.now();
        
        // When
        Account saved = accountRepository.saveAndFlush(account);
        
        LocalDateTime afterSave = LocalDateTime.now();
        
        // Then - Use range check, not exact time
        assertNotNull(saved.getCreatedAt());
        assertTrue(saved.getCreatedAt().isAfter(beforeSave.minusSeconds(1)));
        assertTrue(saved.getCreatedAt().isBefore(afterSave.plusSeconds(1)));
        
        // Wrong: assertEquals(LocalDateTime.now(), saved.getCreatedAt())
        // ^ Fails: timestamps differ by milliseconds
    }
}
```

**Why this works**:
- Temporal assertions use range checks, not exact equality
- `isAfter()`, `isBefore()` handle timing variability
- Prevents flaky tests (random failures)
- More realistic: accepts reasonable timestamp differences

---

## Testing Coverage by Layer

| Layer | Coverage Target | How to Achieve | Example |
|-------|---|---|---|
| **Domain** | 90%+ | Unit test entities, enums, value objects | Validate AccountStatus enum, test entity getters |
| **Application** | 85%+ | Test services with mocked repositories | Mock repository, test business logic |
| **Infrastructure** | 70%+ | Use @DataJpaTest for repository queries | Verify custom @Query executes correctly |
| **Presentation** | 70%+ | Use @WebMvcTest with MockMvc | Verify controller returns correct HTTP status |

---

## References

- Related: [spring-data-jpa-repositories](./skill-spring-data-jpa-repositories.md) — Writing queries that are testable
- Related: [spring-security-oauth2](./skill-spring-security-oauth2.md) — Testing secured endpoints with @WithMockUser
- External: [JUnit 5 Documentation](https://junit.org/junit5/)
- External: [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- External: [TestContainers Documentation](https://www.testcontainers.org/)
- External: [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
