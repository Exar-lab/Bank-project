---
name: java-exception-handling
description: >
  Abstract exception hierarchy for banco-service: BankingException → feature abstract → concrete leaf with errorCode + httpStatus + metadata.
  Trigger: When creating exceptions, handling errors, or building @RestControllerAdvice exception handlers.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Creating a new exception class
- Adding a new feature that needs its own exception type
- Building `@RestControllerAdvice` to translate exceptions to HTTP responses
- Catching exceptions in services or controllers

---

## Critical Patterns

### Rule 1: Hierarchy Is Abstract Classes (NOT sealed)

The actual pattern uses `abstract class` with `extends`, NOT Java `sealed` classes.

```
RuntimeException
└── BankingException (abstract) — base: message, errorCode, httpStatus, metadata
    ├── AuthenticationException (abstract) → InvalidCredentialsException, TokenExpiredException, TokenMalformedException
    ├── ValidationException (abstract) → InvalidCurrencyException, InvalidAmountException
    ├── FraudException (abstract) → FraudDetectedException
    ├── AccountException (abstract) → AccountNotFoundException, AccountBlockedException, InsufficientFundsException
    ├── TransactionException (abstract) → TransactionNotFoundException, DuplicateTransactionException
    ├── EnvelopeException (abstract) → EnvelopeNotFoundException, EnvelopeOverBudgetException
    ├── CardException (abstract) → CardNotFoundException, CardBlockedException, CardExpiredException
    ├── UserException (abstract) → UserNotFoundException, UserAlreadyExistException
    └── RoleException (abstract) → RoleNotFoundException, RoleLevelMismatchException
```

Global base and feature-agnostic abstracts live in `com.banco.co.exception/`.
Feature-specific abstracts and leaves live in `com.banco.co.{feature}/exception/`.

### Rule 2: BankingException Base Structure

```java
// com/banco/co/exception/BankingException.java
public abstract class BankingException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> metadata = new HashMap<>();

    protected BankingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BankingException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // Fluent builder for metadata
    public BankingException addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
}
```

### Rule 3: Feature Abstract Class

One abstract class per feature, extends `BankingException`:

```java
// com/banco/co/account/exception/AccountException.java
public abstract class AccountException extends BankingException {
    protected AccountException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }
}
```

### Rule 4: Concrete Leaf Exception

Each concrete exception has:
- `private static final String ERROR_CODE` in `SCREAMING_SNAKE_CASE`
- `private static final HttpStatus STATUS` — set once, never per-instance
- Constructor takes contextual args, formats the message, calls `super()`
- Calls `addMetadata()` for structured error details

```java
// com/banco/co/account/exception/AccountNotFoundException.java
public class AccountNotFoundException extends AccountException {
    private static final String ERROR_CODE = "ACCOUNT_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    public AccountNotFoundException(String accountCode) {
        super(String.format("Account with code '%s' not found", accountCode), ERROR_CODE, STATUS);
        this.addMetadata("accountCode", accountCode);
    }
}

// com/banco/co/account/exception/AccountBlockedException.java
public class AccountBlockedException extends AccountException {
    private static final String ERROR_CODE = "ACCOUNT_BLOCKED";
    private static final HttpStatus STATUS = HttpStatus.FORBIDDEN;

    public AccountBlockedException(String accountId) {
        super(String.format("Account %s is blocked and cannot process operations", accountId), ERROR_CODE, STATUS);
        this.addMetadata("accountId", accountId);
    }
}

// com/banco/co/account/exception/InsufficientFundsException.java
public class InsufficientFundsException extends AccountException {
    private static final String ERROR_CODE = "INSUFFICIENT_FUNDS";
    private static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    public InsufficientFundsException(String accountId, BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient funds in account %s: requested %.2f, available %.2f",
            accountId, requested, available), ERROR_CODE, STATUS);
        this.addMetadata("accountId", accountId);
        this.addMetadata("requested", requested);
        this.addMetadata("available", available);
    }
}
```

### Rule 5: HTTP Status Conventions

| Situation | HTTP Status |
|---|---|
| Resource not found | `404 NOT_FOUND` |
| Blocked/forbidden resource | `403 FORBIDDEN` |
| Already exists / conflict | `409 CONFLICT` |
| Invalid input / validation failed | `400 BAD_REQUEST` |
| Unprocessable (valid input, business rule fails) | `422 UNPROCESSABLE_ENTITY` |
| Authentication failed / bad credentials | `401 UNAUTHORIZED` |
| Token expired | `401 UNAUTHORIZED` |
| Fraud detected | `403 FORBIDDEN` |

### Rule 6: @RestControllerAdvice — Exception to HTTP Translation

```java
// com/banco/co/exception/GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponseDto> handleBankingException(BankingException ex) {
        log.error("Banking exception: {} — {}", ex.getErrorCode(), ex.getMessage());
        ErrorResponseDto body = new ErrorResponseDto(
            ex.getErrorCode(),
            ex.getMessage(),
            ex.getMetadata(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
            ));
        ErrorResponseDto body = new ErrorResponseDto(
            "VALIDATION_FAILED",
            "Request validation failed",
            fieldErrors,
            LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(body);
    }
}

// ErrorResponseDto.java — the error response record
public record ErrorResponseDto(
    String errorCode,
    String message,
    Map<String, Object> details,
    LocalDateTime timestamp
) {}
```

---

## Code Examples

### Creating a New Feature Exception Set (`loan`)

```java
// 1. Abstract base for feature
// com/banco/co/loan/exception/LoanException.java
public abstract class LoanException extends BankingException {
    protected LoanException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }
}

// 2. Concrete leaves
// com/banco/co/loan/exception/LoanNotFoundException.java
public class LoanNotFoundException extends LoanException {
    private static final String ERROR_CODE = "LOAN_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    public LoanNotFoundException(UUID loanId) {
        super(String.format("Loan %s not found", loanId), ERROR_CODE, STATUS);
        this.addMetadata("loanId", loanId.toString());
    }
}

// com/banco/co/loan/exception/LoanAlreadyPaidException.java
public class LoanAlreadyPaidException extends LoanException {
    private static final String ERROR_CODE = "LOAN_ALREADY_PAID";
    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    public LoanAlreadyPaidException(UUID loanId) {
        super(String.format("Loan %s is already fully paid", loanId), ERROR_CODE, STATUS);
        this.addMetadata("loanId", loanId.toString());
    }
}
```

---

## Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| `throw new RuntimeException("Account not found")` | No error code, no HTTP status, no metadata — useless in API response | `throw new AccountNotFoundException(code)` |
| `sealed` class hierarchy | This project uses `abstract` — don't introduce sealed | Use `abstract class` extends chain |
| Catching `BankingException` in service and swallowing it | Exceptions must bubble to `@RestControllerAdvice` | Let them propagate unless you need to re-wrap |
| `httpStatus` set per-instance in constructor | Status is per-exception TYPE, not per-instance | `private static final HttpStatus STATUS = ...` |
| Error codes with spaces or mixed case | Hard to parse in clients | `SCREAMING_SNAKE_CASE`: `ACCOUNT_NOT_FOUND` |
| Putting feature exceptions in global `exception/` package | Breaks Screaming Architecture | Feature exceptions → `{feature}/exception/` |
| `try/catch` in repository layer | Swallows persistence errors | Let Spring/Hibernate propagate; catch in service if needed |

---

## Resources

- `skill-hexagonal-architecture.md` — Where exception classes live per feature
- `skill-java-optional-handling.md` — `orElseThrow()` with domain exceptions
- Spring `@RestControllerAdvice` docs: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html
