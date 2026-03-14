---
name: java-exception-handling
description: >
  Sealed exception hierarchies and global exception handlers.
  Trigger: When defining exceptions or error handling.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [java21, exceptions, error-handling, sealed-classes]
---

## Pattern: Domain-Specific Exceptions

Use sealed classes for exception hierarchies (Java 17+):

```java
// ✅ CORRECT - Sealed exception hierarchy
public abstract sealed class BankingException extends RuntimeException 
    permits AccountNotFoundException,
            InsufficientFundsException,
            InvalidBalanceException,
            TransferLimitExceededException {
    
    public BankingException(String message) {
        super(message);
    }
    
    public BankingException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Specific exceptions
public final class AccountNotFoundException extends BankingException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}

public final class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

public final class InvalidBalanceException extends BankingException {
    public InvalidBalanceException(String message) {
        super(message);
    }
}

// ❌ WRONG - Generic exceptions
throw new Exception("Something went wrong");
throw new RuntimeException("Error");
```

## Domain Logic Usage

```java
public Account withdraw(BigDecimal amount) {
    if (amount.compareTo(ZERO) <= 0) {
        throw new InvalidBalanceException("Amount must be positive");
    }
    if (balance.compareTo(amount) < 0) {
        throw new InsufficientFundsException("Insufficient funds");
    }
    return new Account(id, holder, type, balance.subtract(amount), createdAt, updatedAt);
}
```

## Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
        AccountNotFoundException ex,
        HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.NOT_FOUND.value(),
            "ACCOUNT_NOT_FOUND",
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
        InsufficientFundsException ex,
        HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            "INSUFFICIENT_FUNDS",
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

// Error response DTO
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {}
```

## Testing Exceptions

```java
@Test
void shouldThrowAccountNotFound() {
    assertThatThrownBy(() -> accountService.getAccount(invalidId))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessage("Account: " + invalidId);
}
```

## Key Rules

1. ✅ Create specific exception classes per error type
2. ✅ Use sealed classes for hierarchy control
3. ✅ Throw from domain layer
4. ✅ Handle globally in @RestControllerAdvice
5. ✅ Return appropriate HTTP status codes
6. ❌ Never catch and swallow exceptions
7. ❌ Never catch generic Exception in application code
