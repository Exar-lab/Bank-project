## When to Use

- Creating or modifying JPA @Entity classes
- Writing Spring Data repository interfaces
- Writing @Query annotations (JPQL or native SQL)
- Adding @Transactional to services
- Implementing the Outbox pattern for reliable Kafka publishing
- Preventing N+1 queries when fetching related entities

## Critical Patterns

### ✅ Correct Pattern — JPA Entity

```java
package com.banco.co.account.repository;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts",
    indexes = {
        @Index(name = "idx_account_holder", columnList = "account_holder"),
        @Index(name = "idx_account_status", columnList = "status")
    }
)
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_holder", nullable = false, length = 100)
    private String accountHolder;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters — JPA requires them (no Lombok @Data)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAccountHolder() { return accountHolder; }
    public void setAccountHolder(String accountHolder) { this.accountHolder = accountHolder; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### ❌ Common Mistake — @Data on Entity

```java
// NEVER DO THIS
@Entity
@Data  // generates equals/hashCode using all fields — breaks JPA identity model
public class AccountEntity {
    @Id UUID id;
    // ...
}
```

**Why it's wrong**: Lombok `@Data` generates `equals()` and `hashCode()` based on all fields. JPA entities must use identity-based equality (by `id`). Also generates a mutable `toString()` that can trigger lazy loading.

---

### ✅ Correct Pattern — N+1 Prevention with JOIN FETCH

```java
package com.banco.co.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAccountRepository extends JpaRepository<AccountEntity, UUID> {

    // Simple derived query — Spring Data handles it
    Optional<AccountEntity> findByAccountHolder(String accountHolder);

    // JOIN FETCH to avoid N+1 when loading transactions
    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.transactions WHERE a.id = :id")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findByIdWithTransactions(@Param("id") UUID id);

    // DTO projection query — only fetch what you need
    @Query("""
        SELECT new com.banco.co.account.dto.AccountSummaryDto(
            a.id, a.accountHolder, COUNT(t.id), SUM(t.amount)
        )
        FROM AccountEntity a
        LEFT JOIN a.transactions t
        GROUP BY a.id, a.accountHolder
        """)
    @Transactional(readOnly = true)
    List<AccountSummaryDto> findAllAccountSummaries();
}
```

### ❌ Common Mistake — Lazy Loading in a Loop (N+1)

```java
// NEVER DO THIS
@Service
public class AccountReportService {
    public List<AccountReportDto> generateReport() {
        List<AccountEntity> accounts = accountRepository.findAll(); // 1 query
        return accounts.stream()
            .map(a -> {
                List<TransactionEntity> txs = a.getTransactions(); // N queries — one per account!
                return new AccountReportDto(a.getAccountHolder(), txs.size());
            })
            .toList();
    }
}
```

**Fix**: Use `findAllWithTransactions()` with JOIN FETCH, or use DTO projection query.

---

### ✅ Correct Pattern — @Transactional

```java
package com.banco.co.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    // Read operations: readOnly=true — enables DB read replicas, disables dirty tracking
    @Transactional(readOnly = true)
    public AccountResponseDto findById(UUID id) {
        return accountRepository.findById(id)
            .map(accountMapper::toResponseDto)
            .orElseThrow(() -> new AccountNotFoundException(id.toString()));
    }

    // Write operations: default @Transactional (readOnly=false)
    @Transactional
    public AccountResponseDto createAccount(CreateAccountDto dto) {
        var entity = accountMapper.toEntity(dto);
        var saved = accountRepository.save(entity);
        return accountMapper.toResponseDto(saved);
    }

    // Multiple writes in one transaction — all commit or all rollback
    @Transactional
    public TransferResponseDto transfer(TransferDto dto) {
        AccountEntity source = accountRepository.findById(dto.sourceAccountId())
            .orElseThrow(() -> new AccountNotFoundException(dto.sourceAccountId().toString()));
        AccountEntity dest = accountRepository.findById(dto.destinationAccountId())
            .orElseThrow(() -> new AccountNotFoundException(dto.destinationAccountId().toString()));

        if (source.getBalance().compareTo(dto.amount()) < 0) {
            throw new InsufficientFundsException(source.getId().toString(),
                dto.amount(), source.getBalance());
        }

        source.setBalance(source.getBalance().subtract(dto.amount()));
        dest.setBalance(dest.getBalance().add(dto.amount()));

        accountRepository.save(source);
        accountRepository.save(dest);

        return new TransferResponseDto(source.getId(), dest.getId(), dto.amount(), "COMPLETED");
    }
}
```

---

### ✅ Correct Pattern — Outbox Entity for Reliable Kafka

```java
package com.banco.co.transaction.repository;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

// Outbox pattern: write domain event to DB in same transaction as business data
@Entity
@Table(name = "outbox_events",
    indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at")
    }
)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;  // e.g., "Transaction"

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;  // e.g., "TransactionCreated"

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;  // JSON serialized event

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;  // PENDING, SENT, FAILED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = OutboxStatus.PENDING;
    }

    // Getters/setters omitted for brevity
}
```

## Examples for Banco-Service

- `com.banco.co.account.repository.AccountEntity` — @Entity with proper column definitions, no @Data
- `com.banco.co.account.repository.IAccountRepository` — JpaRepository with JOIN FETCH queries
- `com.banco.co.account.model.Account` — @Entity with @EntityListeners(AuditingEntityListener.class)
- `com.banco.co.transaction.model.Transaction` — @Entity with @ManyToOne to Account
- `com.banco.co.account.service.AccountService` — @Transactional(readOnly=true) for reads
- `com.banco.co.transaction.service.TransactionService` — @Transactional for multi-step writes

## Best Practices

- NEVER use `@Data` on a JPA entity. Implement getters/setters explicitly, or use `@Getter`/`@Setter` individually.
- ALL JPA entities go in `{feature}/model/`. This is the current project convention — Account, Transaction, Card, User, etc. all live in their respective `model/` packages.
- Always add `@Index` annotations for columns used in WHERE clauses — especially `status`, foreign keys, and lookup fields.
- Use `@Transactional(readOnly = true)` for ALL read-only service methods. Enables performance optimizations.
- Prevent N+1: use `JOIN FETCH` in @Query for collection relationships. Use DTO projections with `new` constructor.
- `Optional.get()` is BANNED. Always use `orElseThrow()` with a domain exception.
- Outbox pattern for Kafka: save `OutboxEventEntity` in the same `@Transactional` as the business entity. A scheduler/relay publishes it later.
- MySQL specifics: use `ENGINE=InnoDB` (default in MySQL 8), charset `utf8mb4`, `precision = 19, scale = 4` for monetary amounts.
- Never call `entityManager.flush()` or `entityManager.clear()` manually in services — let Spring manage the lifecycle.
