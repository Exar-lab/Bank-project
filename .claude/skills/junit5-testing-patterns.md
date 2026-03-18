---
name: junit5-testing-patterns
description: >
  JUnit 5 testing conventions for banco-service: naming, coverage targets, and patterns per architecture layer.
  Trigger: When writing tests, setting up test infrastructure, or reviewing test coverage.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Writing a new test class for any layer
- Setting up Testcontainers for infrastructure tests
- Reviewing test coverage gaps
- Deciding which mocking strategy to use per layer

---

## Critical Patterns

### Naming Conventions

| Layer | Type | Pattern | Example |
|---|---|---|---|
| Domain | Unit | `test{Method}_{Condition}_{Expected}` | `testWithdraw_InsufficientFunds_ThrowsException` |
| Application | Unit | `test{Method}_{Condition}_{Expected}` | `testDeposit_ValidAmount_UpdatesBalance` |
| Infrastructure | Integration | `test{Scenario}_{Expected}` | `testFindActiveByCode_ExistingAccount_ReturnsOptional` |
| Presentation | Slice | `test{Scenario}_{Expected}` | `testCreateAccount_ValidData_ReturnsCreated` |

### Coverage Targets

| Layer | Target | Tools |
|---|---|---|
| Domain | **90%** | JUnit 5, NO mocks, NO Spring |
| Application | **85%** | JUnit 5 + Mockito |
| Infrastructure | **70%** | Testcontainers + MySQL |
| Presentation | **75%** | `@WebMvcTest` + `@MockBean` |

---

## Layer-by-Layer Patterns

### Domain Layer — Pure JUnit 5, Zero Mocks

Domain entities have business logic (rich domain model). Test the logic directly — no Spring context, no Mockito.

```java
// AccountTest.java — src/test/java/com/banco/co/account/model/
class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
            .id(UUID.randomUUID())
            .code("ACC-001")
            .balance(BigDecimal.valueOf(1000))
            .overdraftLimit(BigDecimal.valueOf(200))
            .status(AccountStatus.ACTIVE)
            .currency("USD")
            .build();
    }

    @Test
    void testDeposit_PositiveAmount_IncreasesBalance() {
        // given
        BigDecimal depositAmount = BigDecimal.valueOf(500);
        BigDecimal expectedBalance = BigDecimal.valueOf(1500);

        // when
        account.deposit(depositAmount);

        // then
        assertThat(account.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    void testWithdraw_InsufficientFunds_ThrowsException() {
        // given — balance 1000, overdraft 200, requesting 1300 (exceeds 1200 limit)
        BigDecimal tooMuch = BigDecimal.valueOf(1300);

        // when / then
        assertThatThrownBy(() -> account.withdraw(tooMuch))
            .isInstanceOf(InsufficientFundsException.class)
            .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_FUNDS");
    }

    @Test
    void testWithdraw_WithinOverdraft_DecreasesBalance() {
        BigDecimal amount = BigDecimal.valueOf(1100); // balance 1000 + overdraft 200 = 1200 max
        account.withdraw(amount);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(-100));
    }

    @Test
    void testDeposit_ZeroAmount_ThrowsException() {
        assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO))
            .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void testBlock_ActiveAccount_SetsStatusBlocked() {
        account.block("Suspicious activity");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
    }
}
```

### Application Layer — Mockito for Ports

Services orchestrate domain objects. Mock repositories and other services (ports), test the orchestration.

```java
// AccountServiceTest.java
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private IAccountRepository accountRepository;
    @Mock private IAuditLogService auditLogService;
    @Mock private IAccountMapper mapper;

    @InjectMocks
    private AccountService accountService;

    @Test
    void testDeposit_ValidAmount_ReturnsMappedDto() {
        // given
        String code = "ACC-001";
        BigDecimal depositAmount = BigDecimal.valueOf(200);
        Account account = Account.builder().id(UUID.randomUUID()).balance(BigDecimal.valueOf(500)).build();
        AccountResponseDto expectedDto = new AccountResponseDto(/* ... */);

        when(accountRepository.findActiveByCode(code)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(mapper.toDto(account)).thenReturn(expectedDto);

        // when
        AccountResponseDto result = accountService.deposit(code, new DepositRequestDto(depositAmount, "USD"));

        // then
        assertThat(result).isEqualTo(expectedDto);
        verify(auditLogService).log(eq(AuditEvent.DEPOSIT), eq(account.getId()), eq(depositAmount));
        verify(accountRepository).save(account);
    }

    @Test
    void testDeposit_AccountNotFound_ThrowsAccountNotFoundException() {
        // given
        when(accountRepository.findActiveByCode("NOTEXIST")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> accountService.deposit("NOTEXIST", new DepositRequestDto(BigDecimal.TEN, "USD")))
            .isInstanceOf(AccountNotFoundException.class)
            .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void testUpdate_NullFields_DoesNotOverwriteExisting() {
        // given — AccountUpdateDto with all null = no changes
        AccountUpdateDto updateDto = new AccountUpdateDto(null, null, null);
        Account existing = Account.builder().id(UUID.randomUUID()).currency("USD").build();

        when(accountRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(accountRepository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(mock(AccountResponseDto.class));
        doNothing().when(mapper).updateEntityFromDto(updateDto, existing);

        // when
        accountService.update(existing.getId(), updateDto);

        // then
        verify(mapper).updateEntityFromDto(updateDto, existing); // IGNORE strategy in mapper
    }
}
```

### Infrastructure Layer — Testcontainers + MySQL

Test real SQL queries, real JPA mappings, actual persistence behavior.

```java
// IAccountRepositoryTest.java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class IAccountRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("banco_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void registerMySQLProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private IAccountRepository accountRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testFindActiveByCode_ExistingActiveAccount_ReturnsAccount() {
        // given
        Account account = Account.builder()
            .code("ACC-TEST-001")
            .status(AccountStatus.ACTIVE)
            .balance(BigDecimal.valueOf(1000))
            .currency("USD")
            .build();
        entityManager.persistAndFlush(account);

        // when
        Optional<Account> result = accountRepository.findActiveByCode("ACC-TEST-001");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("ACC-TEST-001");
    }

    @Test
    void testFindActiveByCode_BlockedAccount_ReturnsEmpty() {
        // given — blocked account should NOT be returned by findActiveByCode
        Account blocked = Account.builder()
            .code("ACC-BLOCKED")
            .status(AccountStatus.BLOCKED)
            .balance(BigDecimal.ZERO)
            .build();
        entityManager.persistAndFlush(blocked);

        // when
        Optional<Account> result = accountRepository.findActiveByCode("ACC-BLOCKED");

        // then
        assertThat(result).isEmpty();
    }
}
```

### Presentation Layer — @WebMvcTest

Test HTTP layer: status codes, request/response serialization, validation, RBAC.

```java
// AccountControllerTest.java
@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IAccountService accountService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @WithMockUser(roles = "USER")
    void testCreateAccount_ValidData_ReturnsCreated() throws Exception {
        AccountRequestDto request = new AccountRequestDto(AccountType.SAVINGS, "USD", BigDecimal.ZERO);
        AccountResponseDto response = new AccountResponseDto(UUID.randomUUID(), "ACC-001", AccountType.SAVINGS,
            "USD", BigDecimal.ZERO, BigDecimal.ZERO, AccountStatus.ACTIVE, "user@test.com", LocalDateTime.now());

        when(accountService.create(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("ACC-001"))
            .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testCreateAccount_MissingCurrency_ReturnsBadRequest() throws Exception {
        // accountType is null — triggers @NotNull
        String invalidBody = "{\"overdraftLimit\": 0}";

        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testGetAccount_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/ACC-001"))
            .andExpect(status().isUnauthorized());
    }
}
```

---

## Test Class Structure Template

```java
// TestClassName follows: {ClassUnderTest}Test
class AccountServiceTest {

    // — Setup mocks —
    @Mock private IAccountRepository accountRepository;
    @InjectMocks private AccountService accountService;

    @BeforeEach
    void setUp() {
        // any additional setup not handled by @Mock / @InjectMocks
    }

    // — Happy path —
    @Test
    void test{Method}_{SuccessCondition}_{ExpectedResult}() {}

    // — Error paths —
    @Test
    void test{Method}_{ErrorCondition}_{ExpectedThrow}() {}

    // — Edge cases —
    @Test
    void test{Method}_{EdgeCase}_{ExpectedBehavior}() {}
}
```

---

## Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| `@SpringBootTest` on domain/application tests | Loads full context — slow, unnecessary | `@ExtendWith(MockitoExtension.class)` |
| Mocking the class under test | Tests nothing useful | Mock dependencies, construct SUT normally |
| `@Test void test()` (no description) | Can't tell what's being tested | Follow naming convention |
| H2 in-memory for infrastructure tests | H2 dialect != MySQL; hides real bugs | Testcontainers with `mysql:8.0` |
| Testing private methods directly | Indicates SRP violation | Test via public API; refactor if needed |
| `when(...).thenReturn(null)` for Optional | Should return `Optional.empty()` | `when(...).thenReturn(Optional.empty())` |
| No `verify()` for side effects | Service may not have called audit/save | Add `verify(auditLogService).log(...)` |

---

## Resources

- `skill-java-exception-handling.md` — Domain exceptions to assert in `assertThatThrownBy()`
- `skill-java-optional-handling.md` — Mock repository returning `Optional.empty()`
- `skill-java-dependency-injection.md` — Why constructor injection enables clean unit tests
- Testcontainers MySQL: https://java.testcontainers.org/modules/databases/mysql/
- AssertJ docs: https://assertj.github.io/doc/
