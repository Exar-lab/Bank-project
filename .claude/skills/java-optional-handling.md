---
name: java-optional-handling
description: >
  Correct Optional<T> usage in banco-service: always return Optional from repositories, never call .get() bare.
  Trigger: When writing repository methods, service methods, or any code that may return null or an absent value.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Writing a repository query method
- Calling a repository from a service
- Handling a value that might not exist
- Seeing `.get()` called on an `Optional` anywhere

---

## Critical Patterns

### Rule 1: Repositories ALWAYS Return `Optional<T>`

```java
@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID> {
    // CORRECT — Optional<T> return type
    @Query("SELECT a FROM Account a WHERE a.code = :code AND a.status = 'ACTIVE'")
    Optional<Account> findActiveByCode(@Param("code") String code);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE a.id = :id")
    Optional<Account> findWithUserById(@Param("id") UUID id);

    // WRONG — never return null, never return raw T
    Account findByCode(String code);  // returns null if not found — NPE waiting to happen
}
```

### Rule 2: `orElseThrow()` with Domain Exception

The standard pattern for "get or fail" in services:

```java
// CORRECT
Account account = accountRepository.findActiveByCode(code)
    .orElseThrow(() -> new AccountNotFoundException(code));

// WRONG — .get() without guard
Account account = accountRepository.findActiveByCode(code).get();  // NoSuchElementException if empty

// WRONG — .get() with isPresent() (verbose, error-prone)
Optional<Account> opt = accountRepository.findActiveByCode(code);
if (opt.isPresent()) {
    return opt.get();  // technically safe but anti-pattern — use orElseThrow
}
```

### Rule 3: `orElse()` for Defaults

When a default value is acceptable instead of throwing:

```java
// For primitives/simple values
BigDecimal limit = account.getOverdraftLimit().orElse(BigDecimal.ZERO);

// For default objects (computed eagerly — use orElseGet if expensive)
Account defaultAccount = accountRepository.findByCode(code)
    .orElse(Account.defaultAccount());

// For expensive defaults — use orElseGet (lazy evaluation)
Account account = accountRepository.findByCode(code)
    .orElseGet(() -> createDefaultAccount(userId));
```

### Rule 4: Transformations with `map()` / `flatMap()` / `filter()`

Never unwrap Optional just to re-wrap it. Chain operations:

```java
// CORRECT — map transforms T to R while preserving Optional
Optional<AccountResponseDto> result = accountRepository.findActiveByCode(code)
    .map(mapper::toDto);

// CORRECT — filter keeps the value only if predicate is true
Optional<Account> activeAccount = accountRepository.findByCode(code)
    .filter(a -> a.getStatus() == AccountStatus.ACTIVE);

// CORRECT — flatMap when the mapping function itself returns Optional
Optional<User> user = accountRepository.findByCode(code)
    .flatMap(account -> userRepository.findById(account.getUserId()));

// WRONG — manual unwrap-rewrap
Optional<Account> opt = accountRepository.findByCode(code);
if (opt.isPresent()) {
    return Optional.of(mapper.toDto(opt.get()));  // should be .map(mapper::toDto)
}
return Optional.empty();
```

### Rule 5: `ifPresent()` / `ifPresentOrElse()` for Side Effects

When you need to do something with the value but don't need to return it:

```java
// Fire-and-forget side effect
accountRepository.findActiveByCode(code)
    .ifPresent(account -> auditLogService.log(AuditEvent.ACCOUNT_VIEWED, account.getId()));

// Branch on presence
accountRepository.findByCode(code).ifPresentOrElse(
    account -> auditLogService.log(AuditEvent.ACCOUNT_FOUND, account.getId()),
    () -> log.warn("Account not found for code: {}", code)
);
```

### Rule 6: Service Methods NEVER Return Null

Services return `Optional<T>` when the result may be absent, or throw a domain exception:

```java
// CORRECT — service returns Optional when absence is valid
public Optional<AccountResponseDto> findByCode(String code) {
    return accountRepository.findByCode(code).map(mapper::toDto);
}

// CORRECT — service throws when absence is an error
public AccountResponseDto getActiveByCode(String code) {
    return accountRepository.findActiveByCode(code)
        .map(mapper::toDto)
        .orElseThrow(() -> new AccountNotFoundException(code));
}

// WRONG — never return null
public AccountResponseDto getByCode(String code) {
    Optional<Account> account = accountRepository.findByCode(code);
    if (account.isEmpty()) return null;  // caller must null-check — NPE factory
    return mapper.toDto(account.get());
}
```

---

## Code Examples

### Full Service Method: Find → Transform → Throw

```java
@Transactional
@Override
public AccountResponseDto deposit(String code, DepositRequestDto dto) {
    // 1. Find or throw domain exception
    Account account = accountRepository.findActiveByCode(code)
        .orElseThrow(() -> new AccountNotFoundException(code));

    // 2. Guard against blocked accounts
    if (account.getStatus() == AccountStatus.BLOCKED) {
        throw new AccountBlockedException(code);
    }

    // 3. Delegate business logic to entity (rich domain model)
    account.deposit(dto.amount());

    // 4. Persist and return DTO
    return mapper.toDto(accountRepository.save(account));
}
```

### Chained Optional Pipeline

```java
// Look up account → verify active → return DTO, or throw
public AccountResponseDto getActiveAccount(String code) {
    return accountRepository.findByCode(code)
        .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
        .map(mapper::toDto)
        .orElseThrow(() -> new AccountNotFoundException(code));
}
```

---

## Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| `optional.get()` bare | Throws `NoSuchElementException` if empty — unchecked, no context | `orElseThrow(() -> new DomainException(...))` |
| `if (opt.isPresent()) { return opt.get(); }` | Verbose equivalent of `.get()`, same risk | `orElseThrow()` or `.map()` |
| Returning `null` from service | Callers must null-check; null has no type info | Return `Optional<T>` or throw exception |
| `Optional.of(value)` when value might be null | Throws NPE at construction time | `Optional.ofNullable(value)` |
| `Optional` as method parameter | Anti-pattern — callers forced to wrap | Use overloading or nullable parameter + internal guard |
| `Optional` as record component | Not serializable, violates record intent | Use nullable component + `@NotNull`/`@Null` validation |
| `Optional` fields in `@Entity` | Hibernate can't map Optional fields | Use nullable JPA column, wrap in Optional in repository method |

---

## Resources

- `skill-java-exception-handling.md` — Domain exceptions to throw in `orElseThrow()`
- `skill-hexagonal-architecture.md` — Repository layer rules
- Java 11+ Optional Javadoc: https://docs.oracle.com/en/java/docs/api/java.base/java/util/Optional.html
