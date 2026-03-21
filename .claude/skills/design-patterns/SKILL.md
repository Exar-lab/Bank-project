## When to Use

- Deciding how to structure a new feature in banco-service
- Implementing fee calculation, fraud scoring, or account type variants
- Publishing domain events reliably via Kafka
- Choosing between design approaches for a service or component
- Reviewing architecture compliance of feature code

## Critical Patterns

### ✅ Correct Pattern — Hexagonal / Screaming Architecture

Feature-first (Screaming Architecture) with hexagonal layering inside each feature:

```
com.banco.co.
├── account/
│   ├── model/          ← Pure domain: Account, Money (NO Spring, NO JPA)
│   ├── enums/          ← AccountStatus, AccountType
│   ├── exception/      ← AccountException (abstract), AccountNotFoundException, etc.
│   ├── service/        ← AccountService (@Service, uses IAccountRepository)
│   ├── dto/            ← CreateAccountDto, AccountResponseDto (Records)
│   ├── mapper/         ← AccountMapper (MapStruct)
│   ├── repository/     ← AccountEntity (@Entity), IAccountRepository
│   └── controller/     ← AccountController (@RestController)
│
├── transaction/        ← Same structure
├── fraud/
├── card/
├── user/
├── role/
├── security/           ← Cuts across all features (auth/JWT)
└── exception/          ← Global: BankingException, GlobalExceptionHandler
```

### ❌ Common Mistake — Layer-First Packaging

```
// NEVER DO THIS
com.banco.co.
├── controllers/     ← All controllers in one place — breaks feature cohesion
├── services/        ← Mixed concerns, hard to reason about features
├── repositories/
└── models/
```

---

### ✅ Correct Pattern — Strategy Pattern (Fee Calculation)

```java
package com.banco.co.transaction.service;

// Strategy interface — defines the algorithm family
public interface FeeCalculationStrategy {
    BigDecimal calculate(BigDecimal amount, String currency);
}

// Concrete strategies — one per fee type
@Component("flatFeeStrategy")
public class FlatFeeStrategy implements FeeCalculationStrategy {
    private static final BigDecimal FLAT_FEE = new BigDecimal("2.50");

    @Override
    public BigDecimal calculate(BigDecimal amount, String currency) {
        return FLAT_FEE;
    }
}

@Component("percentageFeeStrategy")
public class PercentageFeeStrategy implements FeeCalculationStrategy {
    private static final BigDecimal RATE = new BigDecimal("0.015"); // 1.5%

    @Override
    public BigDecimal calculate(BigDecimal amount, String currency) {
        return amount.multiply(RATE).setScale(4, RoundingMode.HALF_UP);
    }
}

// Context — uses the strategy
@Service
public class TransactionService {

    private final Map<String, FeeCalculationStrategy> strategies;

    public TransactionService(Map<String, FeeCalculationStrategy> strategies) {
        this.strategies = strategies;  // Spring injects all FeeCalculationStrategy beans by name
    }

    public BigDecimal calculateFee(String accountType, BigDecimal amount, String currency) {
        FeeCalculationStrategy strategy = strategies.getOrDefault(
            accountType + "FeeStrategy",
            strategies.get("flatFeeStrategy")  // default fallback
        );
        return strategy.calculate(amount, currency);
    }
}
```

---

### ✅ Correct Pattern — Factory Pattern (Account Type Creation)

```java
package com.banco.co.account.service;

// Factory interface
public interface AccountFactory {
    Account create(CreateAccountDto dto);
}

// Concrete factories per account type
@Component
public class SavingsAccountFactory implements AccountFactory {
    @Override
    public Account create(CreateAccountDto dto) {
        return new Account(dto.accountHolder(), dto.initialBalance(),
            AccountType.SAVINGS, BigDecimal.valueOf(0.03)); // 3% interest
    }
}

@Component
public class CheckingAccountFactory implements AccountFactory {
    @Override
    public Account create(CreateAccountDto dto) {
        return new Account(dto.accountHolder(), dto.initialBalance(),
            AccountType.CHECKING, BigDecimal.ZERO); // no interest
    }
}

// Registry — dispatches to correct factory
@Service
public class AccountCreationService {

    private final Map<AccountType, AccountFactory> factories;

    public AccountCreationService(List<AccountFactory> factoryList) {
        this.factories = factoryList.stream()
            .collect(Collectors.toMap(
                f -> f.getSupportedType(),
                Function.identity()
            ));
    }

    public Account createAccount(CreateAccountDto dto) {
        AccountFactory factory = factories.get(dto.accountType());
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported account type: " + dto.accountType());
        }
        return factory.create(dto);
    }
}
```

---

### ✅ Correct Pattern — Repository Pattern (Port + Adapter)

```java
// Port (interface in the feature — used by Application layer)
package com.banco.co.account.service;

public interface IAccountRepository {
    Optional<Account> findById(UUID id);
    Account save(Account account);
    List<Account> findAll();
}

// Adapter (JPA implementation in infrastructure — implements the port)
package com.banco.co.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAccountRepository extends JpaRepository<AccountEntity, UUID> {
    // Spring Data methods
}

@Repository
public class AccountRepositoryAdapter implements IAccountRepository {

    private final JpaAccountRepository jpaRepo;
    private final AccountMapper mapper;

    public AccountRepositoryAdapter(JpaAccountRepository jpaRepo, AccountMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Account save(Account account) {
        AccountEntity entity = mapper.toEntity(account);
        return mapper.toDomain(jpaRepo.save(entity));
    }
}
```

---

### ✅ Correct Pattern — Outbox Pattern (Reliable Kafka Publishing)

```java
// 1. In the same @Transactional method: save business entity + outbox event
@Transactional
public TransactionResponseDto createTransaction(CreateTransactionDto dto) {
    // Save business data
    TransactionEntity transaction = transactionMapper.toEntity(dto);
    TransactionEntity saved = transactionRepository.save(transaction);

    // Save outbox event in the SAME transaction
    OutboxEventEntity outboxEvent = new OutboxEventEntity(
        "Transaction",
        saved.getId(),
        "TransactionCreated",
        objectMapper.writeValueAsString(new TransactionCreatedEvent(saved.getId(), saved.getAmount()))
    );
    outboxEventRepository.save(outboxEvent);

    return transactionMapper.toResponseDto(saved);
}

// 2. Scheduler picks up PENDING outbox events and publishes to Kafka
@Scheduled(fixedDelay = 5000)
@Transactional
public void processOutboxEvents() {
    List<OutboxEventEntity> pending = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
    for (OutboxEventEntity event : pending) {
        kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload());
        event.setStatus(OutboxStatus.SENT);
        outboxEventRepository.save(event);
    }
}
```

## Examples for Banco-Service

- `com.banco.co.transaction.service.FeeCalculationStrategy` — Strategy for fee types
- `com.banco.co.account.service.AccountFactory` — Factory for account type creation
- `com.banco.co.account.service.IAccountRepository` — Port interface in Application layer
- `com.banco.co.account.repository.AccountRepositoryAdapter` — JPA adapter in Infrastructure
- `com.banco.co.transaction.repository.OutboxEventEntity` — Outbox for Kafka reliability
- `com.banco.co.fraud.service.FraudScoringService` — Strategy for fraud rule evaluation

## Best Practices

- Screaming Architecture is non-negotiable: feature-first packaging, not layer-first.
- Domain model (`{feature}/model/`) ZERO Spring annotations. Pure Java. 100% testable without mocks.
- Strategy pattern when behavior varies by type (fee types, fraud rules, notification channels).
- Factory pattern when object creation varies by type (account types, transaction types).
- Outbox pattern when you need Kafka events to be atomic with database writes.
- Repository pattern (port/adapter) when you want the service to be independent of JPA.
- In banco-service, the simpler approach (direct JpaRepository in service) is acceptable for most features. Full port/adapter only when hexagonal purity is required or multiple storage backends are planned.
- Observer/domain events: use Spring's `ApplicationEventPublisher` for intra-process events, Kafka for inter-service events.
