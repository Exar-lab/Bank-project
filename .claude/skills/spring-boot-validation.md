---
name: spring-boot-validation
description: "Spring validation patterns: @Valid, @Constraint, validation groups, error handling"
trigger: "When validating request DTOs, implementing custom validators, or adding constraints"
layer: presentation
tags:
  - validation
  - constraints
  - dto
  - error-handling
---

# Skill: spring-boot-validation

## When to Use

**Trigger Context**:
- Validating @RequestBody DTOs in REST controllers
- Implementing custom validation rules (@Constraint)
- Validating method parameters
- Using validation groups for different scenarios
- Handling validation errors and returning error messages

**Decision Tree**:
- Need standard constraints? → Use `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Email`
- Need custom validation? → Create custom `@Constraint` annotation
- Need conditional validation? → Use validation groups or custom validators
- Need method-level validation? → Use `@Validated` on class and `@Valid` on parameters
- Need to check validation errors? → Use `BindingResult` parameter in controller

---

## Critical Patterns

### ✅ Correct: DTO with Standard Validation Constraints

```java
package com.banco.co.account.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AccountRequestDto(
    
    @NotNull(message = "Account type is required")
    AccountType accountType,
    
    @NotBlank(message = "Currency is required")
    @Size(max = 3, message = "Currency must not exceed 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be ISO 4217 code (e.g., CRC, USD)")
    String currency,
    
    @DecimalMin(value = "0", message = "Overdraft limit must be zero or positive")
    BigDecimal overdraftLimit,
    
    @DecimalMin(value = "0", message = "Interest rate must be zero or positive")
    @DecimalMax(value = "100", message = "Interest rate must not exceed 100")
    BigDecimal interestRate,
    
    @NotBlank(message = "Document number is required")
    @Size(max = 12, message = "Document number must not exceed 12 characters")
    @Pattern(regexp = "^[0-9]+$", message = "Document number must contain only numbers")
    String documentNumber
    
) {}
```

**Why this works**:
- `@NotNull` prevents null values
- `@NotBlank` prevents empty strings (spaces are blank)
- `@Size` validates string length
- `@Pattern` uses regex for format validation
- `@DecimalMin/@DecimalMax` validate numeric ranges
- Records are immutable, no risk of field mutation
- Custom messages provide clear error feedback

---

### ✅ Correct: @Valid on @RequestBody in Controller

```java
package com.banco.co.account.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    
    private final AccountService accountService;
    
    @PostMapping("/accounts")
    public AccountResponseDto createAccount(
        @Valid @RequestBody AccountRequestDto dto  // Validation happens here
    ) {
        // If validation fails, Spring returns 400 Bad Request automatically
        // If validation passes, dto is guaranteed to be valid
        return accountService.createAccount(dto);
    }
}
```

**Why this works**:
- `@Valid` annotation triggers validation of the DTO
- If validation fails, Spring automatically returns HTTP 400 with error details
- No need to manually check if DTO is valid
- Error message format is standardized (JSON error response)
- Validation happens BEFORE service method is called

---

### ✅ Correct: Custom @Constraint Annotation for Complex Rules

```java
package com.banco.co.account.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidIbanValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIban {
    String message() default "Invalid IBAN format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Validator implementation
public class ValidIbanValidator implements ConstraintValidator<ValidIban, String> {
    
    @Override
    public void initialize(ValidIban annotation) {
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // @NotNull handles nulls
        }
        
        // IBAN: 2 letters (country) + 2 digits (check) + 1-30 alphanumeric
        return value.matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
    }
}

// Usage in DTO
public record TransferRequestDto(
    @NotBlank(message = "Destination IBAN is required")
    @ValidIban  // Custom constraint
    String destinationIban,
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01")
    BigDecimal amount
) {}
```

**Why this works**:
- `@Constraint` links annotation to validator class
- `ConstraintValidator` implements validation logic
- Reusable: can apply `@ValidIban` to any field
- Encapsulates complex logic in single annotation
- Easy to test validator independently

---

### ❌ Wrong: Validation Logic in Service Layer

```java
// WRONG: Validation mixed with business logic
@Service
public class AccountService {
    
    public AccountResponseDto createAccount(AccountRequestDto dto) {
        // Validation scattered across service (error-prone!)
        if (dto.documentNumber() == null || dto.documentNumber().isEmpty()) {
            throw new ValidationException("Document number is required");
        }
        
        if (!dto.documentNumber().matches("^[0-9]+$")) {
            throw new ValidationException("Document number must contain only numbers");
        }
        
        if (dto.currency() == null || dto.currency().length() != 3) {
            throw new ValidationException("Currency must be 3 characters");
        }
        
        // THEN business logic
        Account account = new Account();
        account.setDocumentNumber(dto.documentNumber());
        // ...
        return mapper.toDto(repository.save(account));
    }
}
```

**Why this fails**:
- Validation logic duplicated across multiple services
- Controller doesn't know what validations apply
- Invalid requests reach service (wasting resources)
- Validation error handling inconsistent
- Hard to test: cannot unit-test validator alone
- Mixes concerns: validation ≠ business logic

**Fix**: Use `@Valid` in controller, constraints on DTO

---

### ✅ Correct: Validation Groups for Different Scenarios

```java
package com.banco.co.account.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;

// Define validation groups
public interface CreateGroup extends Default {}
public interface UpdateGroup extends Default {}

public record AccountUpdateDto(
    
    // On CREATE: accountType cannot be changed (ignore)
    // On UPDATE: accountType is required
    @NotNull(groups = CreateGroup.class, message = "Account type is required on creation")
    AccountType accountType,
    
    @Size(max = 100, groups = {CreateGroup.class, UpdateGroup.class})
    String description,
    
    // Only on UPDATE: can change status
    @NotNull(groups = UpdateGroup.class, message = "Status is required on update")
    AccountStatus status
    
) {}

@RestController
public class AccountController {
    
    @PostMapping("/accounts")
    // Validate using CreateGroup
    public AccountResponseDto createAccount(
        @Validated(CreateGroup.class) @RequestBody AccountUpdateDto dto
    ) {
        return accountService.createAccount(dto);
    }
    
    @PutMapping("/accounts/{id}")
    // Validate using UpdateGroup
    public AccountResponseDto updateAccount(
        @PathVariable UUID id,
        @Validated(UpdateGroup.class) @RequestBody AccountUpdateDto dto
    ) {
        return accountService.updateAccount(id, dto);
    }
}
```

**Why this works**:
- Different groups for different scenarios (create vs. update)
- Field can be required in CREATE but optional in UPDATE
- Reuses same DTO class for both operations
- Reduces DTO duplication
- Flexible validation rules per operation

---

### ❌ Wrong: Not Handling Validation Errors

```java
// WRONG: Assumes validation always passes (lazy!)
@RestController
public class AccountController {
    
    @PostMapping("/accounts")
    public AccountResponseDto createAccount(
        @Valid @RequestBody AccountRequestDto dto  // ValidationException silently caught by framework
    ) {
        // What if validation failed? Exception thrown before reaching here
        // Client gets default Spring error response (not user-friendly)
        return accountService.createAccount(dto);
    }
}

// Framework returns something like:
// {
//   "timestamp": "...",
//   "status": 400,
//   "error": "Bad Request",
//   "message": "..."  // Generic, not helpful
// }
```

**Why this fails**:
- Error response format may not match API requirements
- Error messages not localized or custom
- Cannot add business context to errors
- Tests cannot verify specific validation failures

**Fix**: Add `@ExceptionHandler` for validation errors:
```java
@RestControllerAdvice
public class ValidationExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex
    ) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(toList());
        
        ErrorResponse response = new ErrorResponse(
            "Validation failed",
            errors
        );
        
        return ResponseEntity.badRequest().body(response);
    }
}

// Response:
// {
//   "message": "Validation failed",
//   "errors": [
//     "documentNumber: Document number must contain only numbers",
//     "currency: Currency must be ISO 4217 code"
//   ]
// }
```

---

### ✅ Correct: Method-Level Validation

```java
package com.banco.co.account.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Service
@Validated  // Enable method-level validation
public class AccountService {
    
    private final IAccountRepository accountRepository;
    
    // Validate parameter even outside controller
    public AccountResponseDto createAccount(
        @Valid @NotNull(message = "DTO cannot be null")
        AccountRequestDto dto
    ) {
        Account account = new Account();
        account.setDocumentNumber(dto.documentNumber());
        return mapper.toDto(accountRepository.save(account));
    }
    
    // Validate return value
    @Valid
    public AccountResponseDto getAccount(
        @NotNull(message = "Account ID cannot be null") UUID accountId
    ) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
        return mapper.toDto(account);
    }
}
```

**Why this works**:
- `@Validated` on class enables method validation
- `@Valid` validates parameter or return value
- Service-level validation independent of controllers
- Can reuse service across multiple contexts
- Catches bugs earlier (service validates its own inputs)

---

## References

- Related: [spring-boot-mapstruct-dtos](./skill-spring-boot-mapstruct-dtos.md) — Mapping validated DTOs to entities
- Related: [spring-security-oauth2](./skill-spring-security-oauth2.md) — Validating security principals
- External: [Jakarta Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/)
- External: [Spring Validation Guide](https://spring.io/guides/gs/validating-form-input/)
- External: [Custom Validators in Spring](https://www.baeldung.com/spring-custom-validation-annotation)
