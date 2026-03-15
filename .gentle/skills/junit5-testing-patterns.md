---
name: junit5-testing-patterns
description: >
  JUnit 5 best practices with @Nested, @DisplayName, parameterized tests.
  Trigger: When writing unit tests for domain or application layer.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [testing, junit5, best-practices]
---

## Pattern: Domain Tests (No Mocks)

Domain tests are pure JUnit 5, no Spring, no database:

```java
@DisplayName("Account Domain Model")
class AccountTest {
    
    private UUID accountId;
    private Account account;
    
    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("1000.00"),
            LocalDateTime.now()
        );
    }
    
    @Nested
    @DisplayName("Deposit Operations")
    class DepositOperations {
        
        @Test
        @DisplayName("should deposit positive amount successfully")
        void shouldDepositPositiveAmount() {
            Account updated = account.deposit(new BigDecimal("500.00"));
            
            assertThat(updated.balance()).isEqualTo(new BigDecimal("1500.00"));
            assertThat(account.balance()).isEqualTo(new BigDecimal("1000.00")); // unchanged
        }
        
        @ParameterizedTest(name = "should reject amount {0}")
        @ValueSource(strings = {"0", "-100.00", "-0.01"})
        @DisplayName("should reject non-positive amounts")
        void shouldRejectNonPositiveAmounts(String amount) {
            assertThatThrownBy(() -> account.deposit(new BigDecimal(amount)))
                .isInstanceOf(InvalidBalanceException.class);
        }
    }
    
    @Nested
    @DisplayName("Withdrawal Operations")
    class WithdrawalOperations {
        
        @Test
        @DisplayName("should withdraw valid amount")
        void shouldWithdrawValidAmount() {
            Account updated = account.withdraw(new BigDecimal("300.00"));
            
            assertThat(updated.balance()).isEqualTo(new BigDecimal("700.00"));
        }
        
        @Test
        @DisplayName("should throw InsufficientFundsException")
        void shouldThrowInsufficientFunds() {
            assertThatThrownBy(() -> account.withdraw(new BigDecimal("2000.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient funds");
        }
    }
}
```

## Pattern: Parameterized Tests

Test multiple inputs with one test method:

```java
@ParameterizedTest(name = "transfer {0} from account with balance {1}")
@CsvSource({
    "100.00, 1000.00, true",
    "500.00, 500.00, true",
    "1000.01, 1000.00, false"
})
@DisplayName("should validate transfer amounts")
void shouldValidateTransferAmount(BigDecimal amount, BigDecimal balance, boolean canTransfer) {
    Account account = new Account(
        UUID.randomUUID(),
        "John",
        balance,
        LocalDateTime.now()
    );
    
    assertThat(account.canTransfer(amount)).isEqualTo(canTransfer);
}
```

## Pattern: Application Tests (With Mocks)

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Create Account Use Case")
class CreateAccountUseCaseTest {
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private AccountMapper accountMapper;
    
    private CreateAccountUseCase useCase;
    
    @BeforeEach
    void setUp() {
        useCase = new CreateAccountUseCase(accountRepository, accountMapper);
    }
    
    @Test
    @DisplayName("should create account and publish event")
    void shouldCreateAccount() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest(
            "John Doe",
            new BigDecimal("1000.00")
        );
        
        Account saved = new Account(
            UUID.randomUUID(),
            "John Doe",
            new BigDecimal("1000.00"),
            LocalDateTime.now()
        );
        
        when(accountRepository.save(any(Account.class)))
            .thenReturn(saved);
        
        // Act
        useCase.execute(request);
        
        // Assert
        verify(accountRepository, times(1)).save(any(Account.class));
    }
    
    @Test
    @DisplayName("should throw exception for negative balance")
    void shouldThrowForNegativeBalance() {
        CreateAccountRequest request = new CreateAccountRequest(
            "John",
            new BigDecimal("-100.00")
        );
        
        assertThatThrownBy(() -> useCase.execute(request))
            .isInstanceOf(InvalidBalanceException.class);
        
        verify(accountRepository, never()).save(any());
    }
}
```

## Key Patterns

| Feature | Purpose |
|---------|---------|
| `@Nested` | Organize related tests |
| `@DisplayName` | Describe test behavior |
| `@BeforeEach` | Setup before each test |
| `@ParameterizedTest` | Multiple inputs per test |
| `@ValueSource` | Simple value parameters |
| `@CsvSource` | Complex multi-parameter tests |
| `assertThat()` | AssertJ fluent assertions |
| `verify()` | Mock verification |

## AssertJ Assertions

```java
// Basic
assertThat(value).isEqualTo(expected);
assertThat(value).isNotNull();
assertThat(list).hasSize(3);

// Strings
assertThat(text).startsWith("prefix");
assertThat(text).contains("substring");

// Collections
assertThat(list).contains("item1", "item2");
assertThat(list).allMatch(item -> item > 0);

// Exceptions
assertThatThrownBy(() -> method())
    .isInstanceOf(ExceptionType.class)
    .hasMessage("expected message");

// Objects
assertThat(obj).hasFieldOrPropertyWithValue("field", value);
```

## Naming Convention

```java
// Method names describe behavior clearly
shouldXxxWhenYyy()           // ✅ Clear intent
testAccountCreation()        // ❌ Not descriptive
shouldThrowExceptionWhenBalanceIsNegative() // ✅ Perfect
```

## Coverage Goals for Banking

- **Domain tests**: 90%+ coverage
- **Application tests**: 85%+ coverage
- **Infrastructure**: 70%+ coverage (focus on logic)
- **Controllers**: 75%+ coverage
