## When to Use

- Designing a new REST endpoint
- Reviewing an existing controller for API contract compliance
- Deciding HTTP verbs, status codes, and request/response structure
- Adding validation to request DTOs
- Implementing or reviewing GlobalExceptionHandler
- Setting up `@PreAuthorize` on protected endpoints

## Critical Patterns

### ✅ Correct Pattern — HTTP Verb and Status Code Mapping

```java
package com.banco.co.account.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // GET — read, no body, returns 200
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<AccountResponseDto>> findAll() {
        return ResponseEntity.ok(accountService.findAll());
    }

    // GET by ID — 200 if found, 404 handled by GlobalExceptionHandler
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponseDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.findById(id));
    }

    // POST — create, returns 201 Created with Location header ideally
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponseDto> create(
            @Valid @RequestBody CreateAccountDto dto) {  // @Valid is MANDATORY
        AccountResponseDto created = accountService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT — full replace, returns 200 with updated resource
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountDto dto) {
        return ResponseEntity.ok(accountService.updateAccount(id, dto));
    }

    // PATCH — partial update, returns 200 with updated resource
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponseDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountStatusDto dto) {
        return ResponseEntity.ok(accountService.updateStatus(id, dto));
    }

    // DELETE — returns 204 No Content
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}
```

### ❌ Common Mistake — Wrong HTTP Verbs or Missing @Valid

```java
// NEVER DO THIS
@GetMapping("/accounts/create")  // GET for create? No.
public AccountResponseDto createAccount(AccountRequestDto dto) {}  // no @Valid, no ResponseEntity

@PostMapping("/accounts/delete/{id}")  // POST for delete? No.
public String deleteAccount(@PathVariable UUID id) { return "deleted"; }  // return a String? No.
```

---

### ✅ Correct Pattern — Validation on Request DTOs

```java
package com.banco.co.account.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateAccountDto(

    @NotBlank(message = "Account holder is required")
    @Size(max = 100, message = "Account holder must not exceed 100 characters")
    String accountHolder,

    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
    BigDecimal initialBalance,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be ISO 4217 format (e.g., CRC, USD)")
    String currency,

    @NotNull(message = "Account type is required")
    AccountType accountType

) {}
```

### ❌ Common Mistake — No Validation Constraints

```java
// NEVER DO THIS — no constraints = garbage in, garbage out
public record CreateAccountDto(
    String accountHolder,    // null? empty? 10,000 chars?
    BigDecimal initialBalance,  // negative? null?
    String currency          // "XYZABC"? null?
) {}
```

---

### ✅ Correct Pattern — GlobalExceptionHandler

```java
package com.banco.co.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // All BankingExceptions (AccountNotFoundException, InsufficientFundsException, etc.)
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponseDto> handleBankingException(BankingException ex) {
        log.error("Banking exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(new ErrorResponseDto(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getMetadata(),
                LocalDateTime.now()
            ));
    }

    // @Valid failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
            ));
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDto("VALIDATION_FAILED", "Request validation failed",
                fieldErrors, LocalDateTime.now()));
    }

    // Catch-all — NEVER expose stack traces to clients
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);  // log full stack internally
        return ResponseEntity.internalServerError()
            .body(new ErrorResponseDto("INTERNAL_ERROR",
                "An unexpected error occurred",  // NO stack trace in response
                Map.of(), LocalDateTime.now()));
    }
}

public record ErrorResponseDto(
    String errorCode,
    String message,
    Map<String, Object> details,
    LocalDateTime timestamp
) {}
```

### ❌ Common Mistake — Stack Trace in HTTP Response

```java
// NEVER DO THIS
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handle(Exception ex) {
    return ResponseEntity.status(500).body(ex.getMessage()); // may include class names, paths
    // Worse: return ResponseEntity.status(500).body(ex.toString()); // full stack trace
}
```

---

### ✅ Correct Pattern — ProblemDetail (RFC 7807, Spring Boot 3+)

Spring Boot 3+ supports `ProblemDetail` natively as an alternative to custom error DTOs. It's the RFC 7807 standard for HTTP error responses:

```java
// Option A: use ProblemDetail directly in GlobalExceptionHandler
@ExceptionHandler(BankingException.class)
public ResponseEntity<ProblemDetail> handleBankingException(BankingException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
    problem.setTitle(ex.getErrorCode());
    problem.setProperty("metadata", ex.getMetadata());
    problem.setProperty("timestamp", LocalDateTime.now());
    return ResponseEntity.status(ex.getHttpStatus()).body(problem);
}

// Option B: extend ErrorResponseException (Spring MVC integration)
public class AccountNotFoundException extends ErrorResponseException {
    public AccountNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND,
            ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Account not found: " + id), null);
    }
}
```

> **Current approach in banco-service**: Uses custom `ErrorResponseDto` record. `ProblemDetail` is the standard alternative — consider adopting it for new features to align with RFC 7807 and Spring's native support.

---

### ✅ Correct Pattern — URL Versioning

```
/api/v1/accounts          ← current version
/api/v1/transactions
/api/v1/users

// When breaking changes happen:
/api/v2/accounts          ← new version, old v1 still works during transition
```

### ❌ Common Mistake — No Versioning

```
/accounts       ← no version, breaking changes break all clients immediately
/api/accounts   ← no version in URL
```

---

### ✅ Correct Pattern — @PreAuthorize

```java
// Role-based access
@GetMapping("/{id}")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<AccountResponseDto> findById(@PathVariable UUID id) { ... }

// Admin only
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> delete(@PathVariable UUID id) { ... }

// Ownership check (user can only access their own accounts)
@GetMapping("/{id}")
@PreAuthorize("hasRole('USER') and @accountSecurityService.isOwner(#id, authentication)")
public ResponseEntity<AccountResponseDto> findById(@PathVariable UUID id) { ... }

// Enable @PreAuthorize on the class (requires @EnableMethodSecurity)
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController { ... }
```

### ❌ Common Mistake — No @PreAuthorize

```java
// NEVER leave authenticated endpoints without authorization check
@GetMapping("/{id}")
// Missing @PreAuthorize — any authenticated user can access any account
public ResponseEntity<AccountResponseDto> findById(@PathVariable UUID id) { ... }
```

## Examples for Banco-Service

- `com.banco.co.account.controller.AccountController` — correct HTTP verbs, @Valid, @PreAuthorize
- `com.banco.co.exception.GlobalExceptionHandler` — BankingException + validation + catch-all
- `com.banco.co.account.dto.CreateAccountDto` — Record with validation constraints
- `com.banco.co.transaction.controller.TransactionController` — POST /api/v1/transactions returns 201

## Best Practices

- GET: read-only, no body. POST: create, returns 201. PUT: full replace, 200. PATCH: partial, 200. DELETE: 204.
- EVERY `@RequestBody` parameter in a controller MUST have `@Valid`. No exceptions.
- ALL non-public endpoints MUST have `@PreAuthorize`. Missing it is a security BLOCKER.
- NEVER expose stack traces in HTTP responses. The `@ExceptionHandler(Exception.class)` catch-all must return a generic message.
- URL versioning: all endpoints under `/api/v1/`. Use `/api/v2/` only for breaking changes.
- Controllers have ZERO business logic. Only: parse HTTP → call service → return HTTP response.
- Error responses use `ErrorResponseDto` record with `errorCode`, `message`, `details`, `timestamp`.
- `@RestControllerAdvice` in `com.banco.co.exception.GlobalExceptionHandler` handles ALL exceptions. Features do NOT have their own handler classes.
