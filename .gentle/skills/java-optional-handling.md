---
name: java-optional-handling
description: >
  Correct Optional usage without .get() or null checks.
  Trigger: When handling nullable values or repository queries.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [java, optional, nullsafety]
---

## Pattern: Optional Usage

Never use `.get()` or null checks. Use functional methods:

```java
// ✅ CORRECT
public Optional<Account> findAccount(UUID id) {
    return accountRepository.findById(id);
}

public Account getAccountOrThrow(UUID id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new AccountNotFoundException("Account: " + id));
}

// Transform safely
public Optional<BigDecimal> getBalance(UUID accountId) {
    return accountRepository.findById(accountId)
        .map(Account::balance);
}

// Chain operations
accountRepository.findById(id)
    .ifPresent(account -> transfer(account, amount));

// ❌ WRONG
Account account = optional.get(); // Throws if empty!
if (account != null) { } // Defeats purpose
if (optional.isPresent()) optional.get(); // Verbose
```

## Key Methods

| Method | Use Case |
|--------|----------|
| `.map()` | Transform value safely |
| `.flatMap()` | Chain Optional operations |
| `.filter()` | Conditional unwrap |
| `.ifPresent()` | Side effects |
| `.ifPresentOrElse()` | Do A or B |
| `.orElse()` | Default value |
| `.orElseThrow()` | Critical path (domain errors) |
| `.or()` | Alternative Optional |

## Examples

```java
// Safe transformation
Optional<String> name = findAccount(id)
    .map(Account::holder)
    .filter(h -> !h.isBlank());

// Chaining operations
accountRepository.findById(fromId)
    .flatMap(from -> accountRepository.findById(toId)
        .map(to -> transfer(from, to, amount)))
    .ifPresentOrElse(
        result -> log.info("Transfer completed"),
        () -> log.error("Transfer failed")
    );

// Domain critical: use orElseThrow()
Account account = accountRepository.findById(id)
    .orElseThrow(() -> new AccountNotFoundException("ID: " + id));
```

## Never Do This

```java
❌ Optional.of(nullable) // Can throw
❌ optional.get() // Throws NoSuchElementException
❌ optional.isPresent() followed by .get()
❌ Pass Optional as parameter
❌ Return Optional from void methods
```
