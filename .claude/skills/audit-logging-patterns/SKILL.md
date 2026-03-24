## When to Use

- Any `@Service` method that creates, updates, deletes, or changes state on a domain entity
- Authentication events: login success, login failure, logout, password change
- Authorization violations: user accessing another user's resource
- Security events: fraud detection, suspicious activity, unauthorized access
- Admin operations that affect other users' data
- Unauthenticated flows: public registration attempt, failed login (no session yet)

## Critical Patterns

### ✅ Correct Pattern — Inject the Interface, Never the Concrete Class

```java
package com.banco.co.account.service;

import com.banco.co.auditLog.service.IAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    private final IAccountRepository accountRepository;
    private final IAuditLogService auditLogService;  // ← interface, never AuditLogService directly

    // ...
}
```

### ❌ Common Mistake — Injecting the Concrete Class

```java
// NEVER DO THIS
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AuditLogService auditLogService;  // ← concrete class — couples to implementation
}
```

**Why it's wrong**: Injecting the concrete `AuditLogService` couples the consumer to the implementation. If you swap the implementation or mock it in tests, you'd need to change all consumers. The interface `IAuditLogService` is the contract — always inject that.

---

### ✅ Correct Pattern — logSuccess (operation completed normally)

Use when the business operation succeeds. Maps to `status=SUCCESS`, `severity=INFO`.

```java
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import java.util.List;

// After saving the entity and obtaining the entityId:
auditLogService.logSuccess(
        user,
        AuditAction.ACCOUNT_CREATED,
        AuditEntityType.ACCOUNT,
        savedAccount.getId().toString(),
        List.of(
                new AuditLogDetail("message", "Account created successfully"),
                new AuditLogDetail("accountType", account.getAccountType()),
                new AuditLogDetail("accountCode", account.getAccountCode()),
                new AuditLogDetail("newValues", mapper.toJsonString(savedAccount))
        )
);
```

---

### ✅ Correct Pattern — logFailure (business validation failed or security violation)

Use when the operation is rejected by a business rule or the authenticated user tries to access something they shouldn't. Maps to `status=FAILURE`, `severity=WARNING`. Note: `entityId` is omitted (null) — you haven't touched an entity yet.

```java
// Business validation failure — insufficient funds, duplicate type, non-zero balance:
auditLogService.logFailure(
        user,
        AuditAction.ACCOUNT_CREATED_FAILED,
        AuditEntityType.ACCOUNT,
        List.of(
                new AuditLogDetail("message", "Account creation failed: Duplicate types are not allowed"),
                new AuditLogDetail("userEmail", userEmail),
                new AuditLogDetail("accountType", dto.accountType())
        )
);

// Security violation — user accessing another user's resource:
auditLogService.logFailure(
        user,
        AuditAction.ACCOUNT_READ,
        AuditEntityType.ACCOUNT,
        List.of(
                new AuditLogDetail("message", "Security Violation: User attempted to access Account belonging to other User"),
                new AuditLogDetail("userId", user.getId()),
                new AuditLogDetail("userEmail", user.getEmail()),
                new AuditLogDetail("accountCode", account.getAccountCode()),
                new AuditLogDetail("ownerId", account.getUser().getId()),
                new AuditLogDetail("ownerEmail", account.getUser().getEmail())
        )
);
```

### ❌ Common Mistake — Using logSuccess for a Failed Operation

```java
// NEVER DO THIS
if (duplicateDetected) {
    auditLogService.logSuccess(  // ← WRONG method — this was a failure
            user, AuditAction.ACCOUNT_CREATED, AuditEntityType.ACCOUNT, null, details
    );
    throw new AccountDuplicatedTypeException(...);
}
```

**Why it's wrong**: `logSuccess` writes `status=SUCCESS` and `severity=INFO`. A failed validation is `status=FAILURE`, `severity=WARNING`. The audit trail is the forensic record — wrong status corrupts it.

---

### ✅ Correct Pattern — logCritical (security events: fraud, unauthorized system access)

Use for events that require immediate operator attention. Maps to `status=SUCCESS`, `severity=CRITICAL`, `entityType=SECURITY` (always). No `entityId` parameter.

```java
// Fraud detection, brute-force detection, suspicious patterns:
auditLogService.logCritical(
        user,
        AuditAction.FRAUD_DETECTED,
        List.of(
                new AuditLogDetail("message", "Suspicious login pattern: 5 failed attempts in 2 minutes"),
                new AuditLogDetail("userEmail", user.getEmail()),
                new AuditLogDetail("ipAddress", requestIp),
                new AuditLogDetail("attemptCount", "5")
        )
);
```

### ❌ Common Mistake — Using logFailure for Security Events That Require CRITICAL Severity

```java
// NEVER DO THIS for fraud or unauthorized system-level events
auditLogService.logFailure(
        user,
        AuditAction.FRAUD_DETECTED,      // ← fraud is CRITICAL, not WARNING
        AuditEntityType.ACCOUNT,         // ← security events always use SECURITY entity type
        details
);
```

**Why it's wrong**: `logFailure` produces `severity=WARNING`. Fraud and unauthorized-access events must be `severity=CRITICAL` and `entityType=SECURITY` so SIEM tools and alert rules can filter them correctly. Use `logCritical`.

---

### ✅ Correct Pattern — logAnonymous (unauthenticated operations)

Use when there is NO authenticated user in context: failed login (user may not exist), public registration, or any flow that runs before authentication. `user` is set to `null` internally — the record stores `"anonymous"` for email and username.

```java
// Failed login — we don't know if the user exists; there's no session:
auditLogService.logAnonymous(
        AuditAction.LOGIN_FAILED,
        AuditEntityType.USER,
        attemptedEmail,   // entityId = the email that was attempted
        List.of(
                new AuditLogDetail("message", "Login failed: invalid credentials"),
                new AuditLogDetail("attemptedEmail", attemptedEmail)
        )
);

// New user registration (before the User entity is persisted and returned):
auditLogService.logAnonymous(
        AuditAction.CREATE_PROFILE,
        AuditEntityType.USER,
        dto.email(),
        List.of(
                new AuditLogDetail("message", "New user registered"),
                new AuditLogDetail("email", dto.email()),
                new AuditLogDetail("username", dto.username())
        )
);
```

### ❌ Common Mistake — Passing null User to logSuccess or logFailure

```java
// NEVER DO THIS — passing null User to the wrong method
auditLogService.logSuccess(
        null,                            // ← null user, but using logSuccess instead of logAnonymous
        AuditAction.LOGIN_FAILED,
        AuditEntityType.USER,
        attemptedEmail,
        details
);
```

**Why it's wrong**: `logAnonymous` is the explicit contract for user-absent flows. It signals intent to the reader AND correctly sets `status=SUCCESS` and `severity=INFO` to reflect that the system processed an anonymous request (even if the login failed — the system worked, the credentials didn't).

---

### ✅ Correct Pattern — AuditLogDetail Key-Value Context

`AuditLogDetail` is an `@Embeddable` key-value pair. Build meaningful context to make the audit trail useful for forensics.

```java
// Constructor: new AuditLogDetail(String key, Object value)
// value.toString() is called internally — safe for enums, BigDecimal, UUID, etc.

// For CREATE — capture what was created:
List.of(
    new AuditLogDetail("message", "Account created successfully"),
    new AuditLogDetail("accountType", account.getAccountType()),      // enum → toString()
    new AuditLogDetail("accountCode", account.getAccountCode()),
    new AuditLogDetail("newValues", mapper.toJsonString(savedAccount)) // full JSON snapshot
)

// For UPDATE — capture before/after state:
String oldValues = mapper.toJsonString(account);      // snapshot BEFORE mutating
mapper.updateEntityFromDto(dto, account);
String newValues = mapper.toJsonString(savedAccount); // snapshot AFTER saving

List.of(
    new AuditLogDetail("message", "Account updated successfully"),
    new AuditLogDetail("accountCode", account.getAccountCode()),
    new AuditLogDetail("oldValues", oldValues),
    new AuditLogDetail("newValues", newValues)
)

// For status changes — capture transition:
List.of(
    new AuditLogDetail("message", "Admin changed account status"),
    new AuditLogDetail("adminEmail", adminEmail),
    new AuditLogDetail("accountCode", account.getAccountCode()),
    new AuditLogDetail("oldStatus", oldStatus),   // enum → toString()
    new AuditLogDetail("newStatus", status)
)

// For financial operations — always capture amounts:
List.of(
    new AuditLogDetail("message", "Cannot close account. Balance must be 0.00"),
    new AuditLogDetail("accountCode", account.getAccountCode()),
    new AuditLogDetail("currentBalance", account.getBalance())  // BigDecimal → toString()
)
```

### ❌ Common Mistake — Empty or Vague Details

```java
// NEVER DO THIS
auditLogService.logSuccess(
        user,
        AuditAction.ACCOUNT_UPDATED,
        AuditEntityType.ACCOUNT,
        account.getId().toString(),
        List.of(new AuditLogDetail("message", "updated"))  // useless for forensics
);

// ALSO NEVER — null details list
auditLogService.logSuccess(user, action, entityType, entityId, null);  // repository.save() will still work, but audit is useless
```

---

### ✅ Correct Pattern — Audit BEFORE Throwing the Exception

The audit call must come BEFORE `throw`. Once you throw, execution leaves the method.

```java
@Transactional
public void closeAccount(UUID accountId, String userEmail) {

    Account account = accountRepository.findActiveById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

    User user = userService.getEntityUserByEmail(userEmail);

    if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {

        auditLogService.logFailure(     // ← FIRST: log the failure
                user,
                AuditAction.ACCOUNT_CLOSED,
                AuditEntityType.ACCOUNT,
                List.of(
                        new AuditLogDetail("message", "Cannot close account. Balance must be 0.00"),
                        new AuditLogDetail("accountCode", account.getAccountCode()),
                        new AuditLogDetail("currentBalance", account.getBalance())
                )
        );

        throw new AccountNotEmptyException(     // ← THEN: throw the exception
                account.getAccountCode(), account.getBalance()
        );
    }
    // ...
}
```

### ❌ Common Mistake — Auditing After Throwing

```java
// NEVER DO THIS
if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
    throw new AccountNotEmptyException(...);    // ← audit never runs
    auditLogService.logFailure(...);            // ← unreachable code
}
```

---

### ✅ Correct Pattern — Placing Audit Calls in the Service Layer

Audit calls belong in `@Service` classes (Application layer). They observe the outcome of a use case.

```java
// ✅ CORRECT — in AccountService (@Service)
@Transactional
public AccountResponseDto createAccount(AccountRequestDto dto, String userEmail) {
    // ... business logic ...
    Account savedAccount = accountRepository.save(account);
    auditLogService.logSuccess(user, AuditAction.ACCOUNT_CREATED, AuditEntityType.ACCOUNT,
            savedAccount.getId().toString(), details);
    return mapper.toDto(savedAccount);
}
```

### ❌ Common Mistake — Calling Audit Logging in a @RestController

```java
// NEVER DO THIS — Presentation layer must not know about audit
@RestController
public class AccountController {

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponseDto> create(@RequestBody @Valid AccountRequestDto dto,
                                                      Authentication auth) {
        AccountResponseDto response = accountService.createAccount(dto, auth.getName());
        auditLogService.logSuccess(...);  // ← WRONG: controller is not aware of business outcomes
        return ResponseEntity.status(201).body(response);
    }
}
```

**Why it's wrong**: The controller doesn't know whether the service succeeded at the business level, or what entity was persisted. Audit logging requires access to the domain result (entity ID, old/new values) — that context only exists in the service after the operation completes.

---

## Architecture: How the Three Classes Work Together

```
IAuditLogService          AuditLogService            AuditLogProcessor
(interface — inject)  →   (delegates)            →   (@Async + REQUIRES_NEW)
                          logSuccess()               log(user, action, ...)
                          logFailure()                  ↳ try { save() }
                          logCritical()                 ↳ catch { log.error() } // silent
                          logAnonymous()
```

**Why `@Async`**: The audit write runs in a separate thread. The HTTP response is returned to the client without waiting for the DB write to complete. This means audit logging adds **zero latency** to your API.

**Why `Propagation.REQUIRES_NEW`**: The audit record is saved in its own independent transaction. If the main `@Transactional` on the service method rolls back (e.g., an unexpected exception after the audit call), the audit record is still committed. The audit trail is always preserved, even when the business transaction fails.

**Why `try-catch` in `AuditLogProcessor.log()`**: The audit system must NEVER break the business flow. If the audit DB write fails (network issue, DB overload), the error is swallowed and logged at `ERROR` level. The user's operation is not affected.

```java
// AuditLogProcessor.log() — the try-catch is intentional, not lazy error handling
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(User user, AuditAction action, AuditEntityType entityType,
                String entityId, AuditStatus status, AuditSeverity severity,
                List<AuditLogDetail> details) {
    try {
        // ... build and save AuditLog ...
        auditLogRepository.save(auditLog);
    } catch (Exception e) {
        // Audit NEVER breaks business flow
        log.error("Failed to create audit log: {}", e.getMessage(), e);
    }
}
```

---

## Examples for Banco-Service

### AccountService — createAccount (CREATE with success + failure paths)

```java
// Failure path — duplicate account type detected
if (accountRepository.existsByUser_EmailAndAccountType(userEmail, dto.accountType())) {

    auditLogService.logFailure(
            user,
            AuditAction.ACCOUNT_CREATED_FAILED,
            AuditEntityType.ACCOUNT,
            List.of(
                    new AuditLogDetail("message", "Account creation failed: Duplicate types are not allowed"),
                    new AuditLogDetail("userEmail", userEmail),
                    new AuditLogDetail("accountType", dto.accountType())
            )
    );

    throw new AccountDuplicatedTypeException(userEmail, dto.accountType());
}

// Success path — after save
String newValues = mapper.toJsonString(savedAccount);

auditLogService.logSuccess(
        user,
        AuditAction.ACCOUNT_CREATED,
        AuditEntityType.ACCOUNT,
        savedAccount.getId().toString(),
        List.of(
                new AuditLogDetail("message", "Account created successfully"),
                new AuditLogDetail("accountType", account.getAccountType()),
                new AuditLogDetail("accountCode", account.getAccountCode()),
                new AuditLogDetail("newValues", newValues)
        )
);
```

### AccountService — updateAccount (UPDATE with oldValues/newValues diff)

```java
String oldValues = mapper.toJsonString(account);   // snapshot BEFORE mutation
mapper.updateEntityFromDto(dto, account);
Account savedAccount = accountRepository.save(account);
String newValues = mapper.toJsonString(savedAccount);

auditLogService.logSuccess(
        user,
        AuditAction.ACCOUNT_UPDATED,
        AuditEntityType.ACCOUNT,
        account.getId().toString(),
        List.of(
                new AuditLogDetail("message", "Account updated successfully"),
                new AuditLogDetail("accountCode", account.getAccountCode()),
                new AuditLogDetail("oldValues", oldValues),
                new AuditLogDetail("newValues", newValues)
        )
);
```

### AccountService — validateOwnership (SECURITY violation)

```java
private void validateOwnership(Account account, User user, AuditAction auditAction) {
    if (!account.getUser().getId().equals(user.getId())) {

        auditLogService.logFailure(
                user,
                auditAction,
                AuditEntityType.ACCOUNT,
                List.of(
                        new AuditLogDetail("message", "Security Violation: User attempted to access Account belonging to other User"),
                        new AuditLogDetail("userId", user.getId()),
                        new AuditLogDetail("userEmail", user.getEmail()),
                        new AuditLogDetail("accountCode", account.getAccountCode()),
                        new AuditLogDetail("ownerId", account.getUser().getId()),
                        new AuditLogDetail("ownerEmail", account.getUser().getEmail())
                )
        );

        throw new UnauthorizedException("You don't own this account");
    }
}
```

### AccountService — admin closeAccountByAdmin (admin action with balance context)

```java
auditLogService.logSuccess(
        admin,
        AuditAction.ACCOUNT_CLOSED_BY_ADMIN,
        AuditEntityType.ACCOUNT,
        accountId.toString(),
        List.of(
                new AuditLogDetail("message", "Admin closed account"),
                new AuditLogDetail("adminEmail", adminEmail),
                new AuditLogDetail("accountCode", account.getAccountCode()),
                new AuditLogDetail("balance", account.getBalance())
        )
);
```

---

## Method Decision Table

| Situation | Method | Status | Severity | EntityType |
|-----------|--------|--------|----------|------------|
| Operation succeeded | `logSuccess` | SUCCESS | INFO | domain entity |
| Business rule blocked it | `logFailure` | FAILURE | WARNING | domain entity |
| User accessed wrong resource | `logFailure` | FAILURE | WARNING | domain entity |
| Fraud / brute-force / system intrusion | `logCritical` | SUCCESS | CRITICAL | SECURITY (fixed) |
| No authenticated user in context | `logAnonymous` | SUCCESS | INFO | domain entity |

---

## Best Practices

- ALWAYS inject `IAuditLogService`, never `AuditLogService` directly. This is the Dependency Inversion principle.
- Place audit calls in the **Application layer** (`@Service`). NEVER in `@RestController` — the presentation layer has no access to business outcomes or persisted entity IDs.
- Log BEFORE you throw. The exception unwinds the call stack — audit calls after `throw` are unreachable dead code.
- Use `oldValues` + `newValues` for ALL update operations. JSON snapshots are the minimum required for a useful forensic trail.
- Never log sensitive data in `AuditLogDetail`: no passwords, no tokens, no card PAN numbers, no JWT contents. Log the user's email or ID instead.
- The `@Async` + `Propagation.REQUIRES_NEW` combination in `AuditLogProcessor` means: the audit ALWAYS commits (even on main TX rollback) and NEVER blocks the response. Do not bypass this by calling `AuditLogProcessor` directly.
- `logAnonymous` is NOT just for "anonymous users" — it is the correct method for ANY flow where a `User` entity is not yet available in the request context (pre-auth, public endpoints, failed logins).
- For security violation patterns (user accessing another user's resource), prefer `logFailure` over `logCritical`. Reserve `logCritical` for system-level threats like fraud detection or brute-force. This keeps alert noise low.

### Future Enhancement: AuditorAware for @CreatedBy / @LastModifiedBy

Spring Data JPA provides `@CreatedBy` / `@LastModifiedBy` annotations that auto-populate the modifier from the security context via an `AuditorAware<T>` bean. This is NOT currently implemented in banco-service (audit is handled explicitly via `IAuditLogService`), but it is the Spring Data best practice for tracking who last modified an entity at the JPA level:

```java
// Future pattern — NOT yet in banco-service
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName);
    }
}

// On the entity — complementary to IAuditLogService, not a replacement:
@CreatedBy
@Column(name = "created_by", updatable = false)
private String createdBy;

@LastModifiedBy
@Column(name = "last_modified_by")
private String lastModifiedBy;
```

`AuditorAware` fills JPA-level fields. `IAuditLogService` captures business-level events with full context (old/new values, IP, device, severity). Both are complementary — `AuditorAware` is the lightweight "who touched this row" stamp; `IAuditLogService` is the full forensic event log.

### Future Enhancement: ContextPropagatingTaskDecorator for @Async Observability

When using `@Async`, the security context and MDC trace context are NOT automatically propagated to the new thread. This means audit logs may lose the trace ID in distributed tracing systems (Zipkin, Jaeger, OpenTelemetry). The fix is a `TaskDecorator`:

```java
// Future pattern — NOT yet in banco-service
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator()); // propagates MDC + SecurityContext
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setThreadNamePrefix("audit-async-");
        executor.initialize();
        return executor;
    }
}
```

Without this, audit log threads will have a different or empty trace ID compared to the HTTP request thread — making correlation in observability tools harder.
