## When to Use

- Naming classes, methods, variables, or constants
- Deciding how to structure a method that is getting too long
- Choosing between throwing an exception or returning Optional
- Building an exception hierarchy for a new feature
- Reviewing code quality beyond just compilation

## Critical Patterns

### ✅ Correct Pattern — Naming Conventions

```java
// Classes: nouns, PascalCase
public class AccountService {}
public class TransactionMapper {}
public record CreateAccountDto() {}
public class InsufficientFundsException extends AccountException {}

// Methods: verbs, camelCase
public AccountResponseDto createAccount(CreateAccountDto dto) {}
public Optional<AccountEntity> findByHolder(String holder) {}
public void publishTransactionCreated(TransactionCreatedEvent event) {}

// Booleans: isX, hasX, canX
public boolean isActive() { return status == AccountStatus.ACTIVE; }
public boolean hasSufficientFunds(BigDecimal amount) {
    return this.balance.compareTo(amount) >= 0;
}
public boolean canTransfer() { return isActive() && !isBlocked(); }

// Constants: SCREAMING_SNAKE_CASE
private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";
private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("10.00");
private static final int MAX_TRANSFER_ATTEMPTS = 3;

// Enums: PascalCase type, SCREAMING_SNAKE_CASE values
public enum AccountStatus { ACTIVE, INACTIVE, BLOCKED, SUSPENDED }
public enum TransactionType { CREDIT, DEBIT, TRANSFER, REVERSAL }
```

### ❌ Common Mistake — Vague or Misleading Names

```java
// NEVER DO THIS
public AccountResponseDto process(Object o) {}  // what does "process" mean?
public boolean check(Account a) {}              // check WHAT?
public AccountResponseDto getStuff() {}         // getStuff is not a name
private String data;                            // data of what type/purpose?
private boolean flag;                           // flag for what?
private static final int X = 3;                // magic constant, no name
```

---

### ✅ Correct Pattern — Method Length and Single Responsibility

```java
// ✅ Each method does ONE thing, ideally under 20 lines
@Transactional
public TransferResponseDto transfer(TransferDto dto) {
    AccountEntity source = findAccountOrThrow(dto.sourceAccountId());
    AccountEntity destination = findAccountOrThrow(dto.destinationAccountId());
    validateSufficientFunds(source, dto.amount());
    executeTransfer(source, destination, dto.amount());
    return buildTransferResponse(source, destination, dto.amount());
}

private AccountEntity findAccountOrThrow(UUID id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new AccountNotFoundException(id.toString()));
}

private void validateSufficientFunds(AccountEntity account, BigDecimal amount) {
    if (account.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException(
            account.getId().toString(), amount, account.getBalance()
        );
    }
}

private void executeTransfer(AccountEntity source, AccountEntity dest, BigDecimal amount) {
    source.setBalance(source.getBalance().subtract(amount));
    dest.setBalance(dest.getBalance().add(amount));
    accountRepository.save(source);
    accountRepository.save(dest);
}
```

### ❌ Common Mistake — Method that Does Everything

```java
// NEVER DO THIS — 50-line method doing 5 different things
@Transactional
public TransferResponseDto transfer(TransferDto dto) {
    // Finding accounts (could be extracted)
    AccountEntity source = accountRepository.findById(dto.sourceAccountId())
        .orElseThrow(() -> new RuntimeException("not found"));  // wrong exception too
    AccountEntity dest = accountRepository.findById(dto.destinationAccountId())
        .orElseThrow(() -> new RuntimeException("not found"));
    // Validation (could be extracted)
    if (source.getBalance().compareTo(dto.amount()) < 0) {
        throw new RuntimeException("insufficient");  // wrong exception too
    }
    // Business logic
    source.setBalance(source.getBalance().subtract(dto.amount()));
    dest.setBalance(dest.getBalance().add(dto.amount()));
    accountRepository.save(source);
    accountRepository.save(dest);
    // Logging (should use log field)
    System.out.println("Transfer done");  // System.out in a service!
    // Building response (could be mapper)
    TransferResponseDto response = new TransferResponseDto();
    response.setSourceId(source.getId());
    // ... 20 more lines
    return response;
}
```

---

### ✅ Correct Pattern — Optional Without .get()

```java
// orElseThrow — when absence is an error
public AccountResponseDto findById(UUID id) {
    return accountRepository.findById(id)
        .map(accountMapper::toResponseDto)
        .orElseThrow(() -> new AccountNotFoundException(id.toString()));
}

// orElse — when absence has a default
public BigDecimal getBalance(UUID accountId) {
    return accountRepository.findById(accountId)
        .map(AccountEntity::getBalance)
        .orElse(BigDecimal.ZERO);
}

// ifPresent — when you only act if present
public void notifyIfExists(UUID accountId) {
    accountRepository.findById(accountId)
        .ifPresent(account -> notificationService.notify(account.getAccountHolder()));
}

// filter + orElseThrow — when you need conditional logic
public AccountResponseDto findActiveAccount(UUID id) {
    return accountRepository.findById(id)
        .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
        .map(accountMapper::toResponseDto)
        .orElseThrow(() -> new AccountNotFoundException(id.toString()));
}
```

### ❌ Common Mistake — Optional.get()

```java
// NEVER DO THIS
Optional<AccountEntity> account = accountRepository.findById(id);
return accountMapper.toResponseDto(account.get());  // throws NoSuchElementException if empty
```

---

### ✅ Correct Pattern — Abstract Exception Hierarchy

```java
// Global base — abstract, in com.banco.co.exception
public abstract class BankingException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> metadata = new HashMap<>();

    protected BankingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BankingException addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
}

// Feature abstract — in com.banco.co.account.exception
public abstract class AccountException extends BankingException {
    protected AccountException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }
}

// Concrete leaf — in com.banco.co.account.exception
public class InsufficientFundsException extends AccountException {
    private static final String ERROR_CODE = "INSUFFICIENT_FUNDS";
    private static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    public InsufficientFundsException(String accountId, BigDecimal required, BigDecimal available) {
        super(String.format("Insufficient funds in account %s: required %.2f, available %.2f",
            accountId, required, available), ERROR_CODE, STATUS);
        addMetadata("accountId", accountId);
        addMetadata("required", required);
        addMetadata("available", available);
    }
}
```

### ❌ Common Mistake — Non-Abstract Hierarchy or Generic Exception

```java
// NEVER — non-abstract base allows direct instantiation
public class BankingException extends RuntimeException { ... }  // should be abstract

// NEVER — generic RuntimeException
throw new RuntimeException("Account not found");  // no errorCode, no HTTP status

// NEVER — sealed classes (project uses abstract)
public sealed class AccountException permits AccountNotFoundException { ... }
```

---

### ✅ Correct Pattern — Constants Instead of Magic Values

```java
// ✅ Named constants in the class that uses them
@Service
public class FraudEvaluationService {
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("10000.00");
    private static final int MAX_DAILY_TRANSACTIONS = 20;
    private static final String FRAUD_TOPIC = "fraud.detected";

    public FraudScore evaluate(Transaction tx) {
        if (tx.amount().compareTo(HIGH_RISK_THRESHOLD) > 0) {
            return FraudScore.HIGH;
        }
        // ...
    }
}
```

### ❌ Common Mistake — Magic Numbers/Strings

```java
// NEVER DO THIS
if (tx.amount().compareTo(new BigDecimal("10000.00")) > 0) { ... }  // magic number
kafkaTemplate.send("fraud.detected", event);                         // magic string topic
if (attempts > 3) { ... }                                            // magic number
```

## Examples for Banco-Service

- `com.banco.co.account.exception.InsufficientFundsException` — concrete leaf, abstract hierarchy
- `com.banco.co.account.service.AccountService#transfer()` — extracted private methods
- `com.banco.co.account.model.Account#hasSufficientFunds()` — boolean method naming
- `com.banco.co.fraud.service.FraudEvaluationService` — named constants, no magic numbers

## Best Practices

- Classes are nouns. Methods are verbs. Booleans start with `is`/`has`/`can`.
- Constants are `SCREAMING_SNAKE_CASE`. NEVER use magic numbers or magic strings.
- Methods: max ~20 lines ideally. If it does more than one thing, split it.
- Optional: NEVER `.get()`. Always `orElseThrow()`, `orElse()`, `ifPresent()`, or `map()`.
- Exception hierarchy: always abstract base → abstract feature → concrete leaf. NEVER non-abstract base. NEVER sealed.
- Feature exceptions go in `{feature}/exception/`. Global base goes in `com.banco.co.exception/`.
- Package names are feature-first: `com.banco.co.account.*`. NEVER layer-first: `com.banco.co.service.*`.
- No `System.out.println()` anywhere. Use SLF4J.
- Every concrete exception has: `ERROR_CODE` (SCREAMING_SNAKE_CASE), `STATUS` (static final HttpStatus), contextual constructor, `addMetadata()` calls.
