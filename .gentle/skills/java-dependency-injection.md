---
name: java-dependency-injection
description: >
  Constructor injection only. Never use @Autowired on fields.
  Trigger: When creating Spring beans or services.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [spring, dependency-injection, best-practices]
---

## Pattern: Constructor Injection

ONLY use constructor injection. Make fields final.

```java
// ✅ CORRECT - Constructor injection with final fields
@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final EventPublisher eventPublisher;
    
    public TransferService(
        AccountRepository accountRepository,
        TransferRepository transferRepository,
        EventPublisher eventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.eventPublisher = eventPublisher;
    }
    
    public void transfer(UUID fromId, UUID toId, BigDecimal amount) {
        // Use injected dependencies
    }
}

// ❌ WRONG - Field injection
@Service
public class TransferService {
    @Autowired
    private AccountRepository accountRepository; // WRONG!
    
    @Autowired
    private TransferRepository transferRepository; // WRONG!
}

// ❌ WRONG - Setter injection
@Service
public class TransferService {
    private AccountRepository accountRepository;
    
    @Autowired
    public void setRepository(AccountRepository repo) { // WRONG!
        this.accountRepository = repo;
    }
}
```

## Benefits

- ✅ **Immutability**: Fields are `final`
- ✅ **Testability**: Easy to mock in unit tests
- ✅ **Visibility**: Dependencies clear in constructor
- ✅ **Compilation**: Catches missing dependencies at compile time

## For Use Cases

```java
@Service
public class CreateAccountUseCase {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final EventPublisher eventPublisher;
    
    // Single responsibility per use case
    public CreateAccountUseCase(
        AccountRepository accountRepository,
        AccountMapper accountMapper,
        EventPublisher eventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.eventPublisher = eventPublisher;
    }
}
```

## Configuration Class

```java
@Configuration
public class ApplicationConfig {
    
    @Bean
    public CreateAccountUseCase createAccountUseCase(
        AccountRepository accountRepository,
        AccountMapper accountMapper,
        EventPublisher eventPublisher
    ) {
        return new CreateAccountUseCase(
            accountRepository,
            accountMapper,
            eventPublisher
        );
    }
}
```

## For Records + Services

```java
// Combine Records with constructor injection
@Service
public class AccountService {
    private final AccountRepository repository;
    private final AccountMapper mapper;
    
    public AccountService(
        AccountRepository repository,
        AccountMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }
    
    public AccountResponse createAccount(CreateAccountRequest request) {
        // Implementation
    }
}
```
