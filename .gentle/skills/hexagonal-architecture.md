---
name: hexagonal-architecture
description: >
  Clean Architecture layers: Domain, Application, Infrastructure, Presentation.
  Trigger: When designing features or structuring new modules.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [architecture, clean-code, domain-driven-design]
---

## Layer Structure

```
com/banco/
├── domain/              # Core business logic (NO Spring)
│   ├── model/          # Entities, Value Objects
│   ├── service/        # Domain services
│   ├── repository/     # Port interfaces (not impl!)
│   └── exception/      # Domain exceptions
│
├── application/        # Use cases & orchestration
│   ├── service/        # Use cases
│   ├── dto/            # Records (requests/responses)
│   ├── mapper/         # DTO ↔ Domain mappers
│   └── port/           # Outbound ports
│
├── infrastructure/     # External systems (Adapters)
│   ├── persistence/    # Repository implementations
│   ├── messaging/      # Event publishers
│   └── config/         # Spring configuration
│
└── presentation/       # REST controllers
    ├── controller/     # HTTP entry points
    └── rest/           # Exception handlers, responses
```

## Key Principle

```
Domain (core) ← Application (use cases) ← Infrastructure (adapters) ← Presentation (REST)

Dependencies flow INWARD, never outward.
Domain layer knows NOTHING about Spring, JPA, or HTTP.
```

## Example: Account Creation Feature

### 1. Domain Layer (No Spring)

```java
// domain/model/Account.java (Record)
public record Account(UUID id, String holder, BigDecimal balance, LocalDateTime createdAt) {
    public Account {
        Objects.requireNonNull(id);
        Objects.requireNonNull(holder);
        Objects.requireNonNull(balance);
    }
}

// domain/repository/AccountRepository.java (Port interface)
public interface AccountRepository {
    Optional<Account> findById(UUID id);
    Account save(Account account);
}

// domain/exception/AccountException.java
public abstract sealed class AccountException extends RuntimeException 
    permits AccountNotFoundException { }
```

### 2. Application Layer (Use Cases)

```java
// application/dto/CreateAccountRequest.java (Record)
public record CreateAccountRequest(
    @NotBlank String accountHolder,
    @DecimalMin("0.01") BigDecimal initialBalance
) {}

// application/service/CreateAccountUseCase.java
@Service
public class CreateAccountUseCase {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    
    public CreateAccountUseCase(
        AccountRepository accountRepository,
        AccountMapper accountMapper
    ) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }
    
    public AccountResponse execute(CreateAccountRequest request) {
        Account account = new Account(
            UUID.randomUUID(),
            request.accountHolder(),
            request.initialBalance(),
            LocalDateTime.now()
        );
        Account saved = accountRepository.save(account);
        return accountMapper.toResponse(saved);
    }
}
```

### 3. Infrastructure Layer (Adapters)

```java
// infrastructure/persistence/jpa/AccountJpaEntity.java
@Entity
@Table(name = "accounts")
public class AccountJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String holder;
    @Column(nullable = false)
    private BigDecimal balance;
}

// infrastructure/persistence/repository/AccountRepositoryImpl.java
@Component
public class AccountRepositoryImpl implements AccountRepository {
    private final AccountJpaRepository jpaRepository;
    private final AccountJpaMapper jpaMapper;
    
    public AccountRepositoryImpl(
        AccountJpaRepository jpaRepository,
        AccountJpaMapper jpaMapper
    ) {
        this.jpaRepository = jpaRepository;
        this.jpaMapper = jpaMapper;
    }
    
    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = jpaMapper.toEntity(account);
        return jpaMapper.toDomain(jpaRepository.save(entity));
    }
}
```

### 4. Presentation Layer (Controllers)

```java
// presentation/controller/AccountController.java
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final CreateAccountUseCase createAccountUseCase;
    
    public AccountController(CreateAccountUseCase createAccountUseCase) {
        this.createAccountUseCase = createAccountUseCase;
    }
    
    @PostMapping
    public ResponseEntity<AccountResponse> create(
        @Valid @RequestBody CreateAccountRequest request
    ) {
        AccountResponse response = createAccountUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

## Testing Strategy

- **Domain tests**: JUnit 5, no mocks
- **Application tests**: Mock repositories, verify domain calls
- **Infrastructure tests**: Testcontainers + real database
- **Controller tests**: MockMvc

## Benefits

✅ Domain logic testable without Spring
✅ Easy to swap implementations (DB, message broker)
✅ Clear separation of concerns
✅ Framework-agnostic business logic
