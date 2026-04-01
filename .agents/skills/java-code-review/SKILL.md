## When to Use

- Reviewing any PR before merge
- Self-reviewing your own code before submitting
- Auditing an existing feature for compliance with project conventions
- Writing tests for any layer of the application

## Critical Patterns

### ✅ Correct Pattern — Code Review Checklist

Run through every item below on every PR. Items marked [BLOCKER] prevent merge.

#### Architecture Layer Violations [BLOCKER]

- [ ] No `@RestController` contains business logic — only HTTP mapping and delegation to service
- [ ] `@Entity` classes live in `{feature}/model/` — this IS the current convention. DO NOT move them to `{feature}/repository/`
- [ ] No `@Service` directly calls another feature's repository (must go through that feature's service)
- [ ] No circular dependency between features (account → transaction is OK; transaction → account AND account → transaction is NOT)
- [ ] No extra Spring annotations (`@Component`, `@Service`, `@Repository`) added to domain model classes beyond `@Entity` + `@EntityListeners`

#### Instant Reject Conditions [BLOCKER]

```java
// 1. Optional.get() — instant reject
Optional<Account> acc = repo.findById(id);
return acc.get();  // BLOCKER — throws NoSuchElementException if empty

// 2. @Autowired field injection — instant reject
@Service
public class BadService {
    @Autowired  // BLOCKER — not testable, not final
    private IAccountRepository repo;
}

// 3. @Data on DTO — instant reject
@Data  // BLOCKER — mutable, broken equals/hashCode for JPA
public class CreateAccountDto { ... }

// 4. Business logic in @RestController — instant reject
@RestController
public class AccountController {
    @PostMapping("/accounts")
    public ResponseEntity<?> create(@RequestBody CreateAccountDto dto) {
        if (dto.balance().compareTo(BigDecimal.ZERO) < 0) {  // BLOCKER — business logic here
            throw new IllegalArgumentException("Balance cannot be negative");
        }
        // ...
    }
}

// 5. catch(Exception e) generic — instant reject
try {
    accountRepository.save(entity);
} catch (Exception e) {  // BLOCKER — swallows everything
    log.error("Error", e);
}

// 6. @Service/@Component in {feature}/model/ — instant reject
// BLOCKER — model/ only allows @Entity + @EntityListeners (current convention)
package com.banco.co.account.model;
@Service  // BLOCKER — wrong annotation in model/
public class Account { ... }
```

---

### ✅ Correct Pattern — Testing by Layer

#### Domain Layer Tests (Target: 90% coverage)

```java
package com.banco.co.account.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

// Pure JUnit 5 — no mocks, no Spring context
class AccountTest {

    @Test
    void testWithdraw_SufficientFunds_ReducesBalance() {
        Account account = new Account("ACC-001", new BigDecimal("1000.00"));
        account.withdraw(new BigDecimal("200.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void testWithdraw_InsufficientFunds_ThrowsException() {
        Account account = new Account("ACC-001", new BigDecimal("100.00"));
        assertThatThrownBy(() -> account.withdraw(new BigDecimal("500.00")))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("ACC-001");
    }
}
```

#### Application Layer Tests (Target: 85% coverage)

```java
package com.banco.co.account.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private IAccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    @Test
    void testFindById_ExistingAccount_ReturnsDto() {
        UUID id = UUID.randomUUID();
        AccountEntity entity = buildEntity(id);
        AccountResponseDto dto = buildDto(id);

        when(accountRepository.findById(id)).thenReturn(Optional.of(entity));
        when(accountMapper.toResponseDto(entity)).thenReturn(dto);

        AccountResponseDto result = accountService.findById(id);

        assertThat(result).isEqualTo(dto);
        verify(accountRepository).findById(id);
    }

    @Test
    void testFindById_NonExistentAccount_ThrowsAccountNotFoundException() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findById(id))
            .isInstanceOf(AccountNotFoundException.class);
    }
}
```

#### Presentation Layer Tests (Target: 75% coverage)

```java
package com.banco.co.account.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;  // Spring Boot 4.x package
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;    // Spring Boot 4.x preferred
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

@WebMvcTest(AccountController.class)  // Only loads web layer — fast
class AccountControllerTest {

    @Autowired
    MockMvcTester mvc;  // MockMvcTester: fluent AssertJ-based API (Spring Boot 4.x)

    @MockitoBean
    AccountService accountService;

    @Test
    void testCreateAccount_ValidRequest_Returns201() {
        given(accountService.createAccount(any())).willReturn(buildResponseDto());

        assertThat(mvc.post().uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "accountHolder": "Juan Perez",
                        "initialBalance": 1000.00,
                        "currency": "CRC"
                    }
                """))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$.accountHolder").isEqualTo("Juan Perez");
    }

    @Test
    void testCreateAccount_MissingAccountHolder_Returns400() {
        assertThat(mvc.post().uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"initialBalance": 1000.00}"""))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testFindById_Unauthenticated_Returns401() {
        // Every protected endpoint needs an unauthorized test
        assertThat(mvc.get().uri("/api/v1/accounts/{id}", UUID.randomUUID()))
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
```

#### Infrastructure Layer Tests (Target: 70% coverage)

```java
// Use Testcontainers for real database — never mock JPA
@SpringBootTest
@Testcontainers
class AccountRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    IAccountRepository accountRepository;

    @Test
    void testSaveAndFindById_ReturnsPersistedEntity() {
        AccountEntity entity = buildEntity();
        AccountEntity saved = accountRepository.save(entity);

        assertThat(accountRepository.findById(saved.getId())).isPresent();
    }
}
```

---

### ❌ Common Mistake — Mocking JPA in Infrastructure Tests

```java
// NEVER DO THIS
@ExtendWith(MockitoExtension.class)
class AccountRepositoryTest {
    @Mock
    IAccountRepository accountRepository;  // Mocking the repo being tested — tests nothing real

    @Test
    void testFindById() {
        when(accountRepository.findById(any())).thenReturn(Optional.of(new AccountEntity()));
        // This test only verifies Mockito works, not the actual repository
    }
}
```

## Examples for Banco-Service

- Domain: `com.banco.co.account.model.AccountTest` — no Spring, no mocks, pure logic
- Application: `com.banco.co.account.service.AccountServiceTest` — Mockito mocks for repos
- Presentation: `com.banco.co.account.controller.AccountControllerTest` — @WebMvcTest
- Infrastructure: `com.banco.co.account.repository.AccountRepositoryTest` — Testcontainers MySQL

## Best Practices

- Instant reject: `Optional.get()`, `@Autowired` field, `@Data` on DTO, business logic in `@RestController`, `catch(Exception e)` generic, `@Service`/`@Component` in `{feature}/model/`.
- Test naming: Unit: `test{Method}_{Condition}_{Expected}`, Integration: `test{Scenario}_{Expected}`.
- Coverage minimums by layer: Domain 90%, Application 85%, Infrastructure 70%, Presentation 75%.
- NEVER mock JPA repositories in infrastructure tests. Use Testcontainers with real MySQL.
- Every new endpoint must have: a happy-path test, a validation failure test (400), and an unauthorized test (401/403).
- Every service method that throws an exception must have a test asserting the exception type and message.
- N+1 queries are BLOCKER: look for `findAll()` followed by `.getX()` on related entities in a loop.
- Missing `@Valid` on `@RequestBody` in a controller is a BLOCKER for any endpoint that receives user input.
- Missing `@PreAuthorize` on an endpoint that requires authentication is a security BLOCKER.
