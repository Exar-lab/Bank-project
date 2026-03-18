---
name: java-records-dtos
description: >
  All DTOs in banco-service are Java Records with inline Jakarta validation. Never use @Data or mutable classes.
  Trigger: When creating or modifying any DTO, request/response class, or data transfer object.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Creating a new DTO (request, response, or update)
- Adding validation to an existing DTO
- Implementing cross-field validation
- Writing a mapper for a DTO
- Reviewing whether a class should be a Record

---

## Critical Patterns

### Rule 1: ALL DTOs Are Records

No exceptions. No `@Data`, no `@Getter/@Setter`, no mutable class with setters.

```java
// CORRECT
public record AccountRequestDto(
    @NotNull AccountType accountType,
    @NotBlank @Size(max = 3) String currency,
    @DecimalMin("0") BigDecimal overdraftLimit
) {}

// WRONG — never do this
@Data
public class AccountRequestDto {
    private AccountType accountType;
    private String currency;
    private BigDecimal overdraftLimit;
}
```

### Rule 2: Jakarta Validation Inline on Components

Annotations go directly on record components, not in a body. Import from `jakarta.validation.constraints.*`.

```java
public record TransactionRequestDto(
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = 3) String currency,
    @NotBlank String description,
    @NotNull TransactionType type,
    @NotNull UUID sourceAccountId
) {}
```

### Rule 3: Cross-Field Validation in Compact Constructor

When one field's validity depends on another, use the compact constructor. This runs at record construction time.

```java
public record TransferRequestDto(
    @NotNull UUID sourceAccountId,
    @NotNull UUID destinationAccountId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = 3) String currency
) {
    // Compact constructor — runs after component validation
    public TransferRequestDto {
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException(
                "Source and destination accounts must be different"
            );
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                "Amount cannot have more than 2 decimal places"
            );
        }
    }
}
```

### Rule 4: Three DTO Types Per Feature

| Type | Purpose | Mapper method |
|---|---|---|
| `{Feature}RequestDto` | Create new resource (POST body) | `toEntity(dto)` |
| `{Feature}ResponseDto` | API response (GET/POST/PUT response) | `toDto(entity)` |
| `{Feature}UpdateDto` | Partial update (PATCH body) — all fields nullable | `updateEntityFromDto(dto, entity)` |

```java
// Request — for creation
public record AccountRequestDto(
    @NotNull AccountType accountType,
    @NotBlank @Size(max = 3) String currency,
    @DecimalMin("0") BigDecimal overdraftLimit
) {}

// Response — what we return to clients (can include computed/joined fields)
public record AccountResponseDto(
    UUID id,
    String code,
    AccountType accountType,
    String currency,
    BigDecimal balance,
    BigDecimal overdraftLimit,
    AccountStatus status,
    String userEmail,       // flattened from Account.user.email
    LocalDateTime createdAt
) {}

// Update — all fields nullable/optional for partial updates
public record AccountUpdateDto(
    AccountType accountType,        // null = keep existing
    BigDecimal overdraftLimit,      // null = keep existing
    AccountStatus status            // null = keep existing
) {}
```

### Rule 5: Update DTO Mapper Uses IGNORE Strategy

For `UpdateDto`, the MapStruct mapper MUST use `NullValuePropertyMappingStrategy.IGNORE` so null fields don't
overwrite existing entity values.

```java
@Mapper(componentModel = "spring")
public interface IAccountMapper {

    // Create: map all fields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)   // code is generated, not from DTO
    @Mapping(target = "user", ignore = true)   // set separately in service
    Account toEntity(AccountRequestDto dto);

    // Read: flatten nested objects
    @Mapping(source = "user.email", target = "userEmail")
    AccountResponseDto toDto(Account account);

    // Update: only set non-null fields from DTO onto existing entity
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(AccountUpdateDto dto, @MappingTarget Account account);
}
```

---

## Code Examples

### Complete DTO Set for a Feature (`card`)

```java
// CardRequestDto.java
public record CardRequestDto(
    @NotNull CardType cardType,
    @NotNull UUID accountId,
    @NotBlank @Size(min = 4, max = 4) String pin
) {}

// CardResponseDto.java
public record CardResponseDto(
    UUID id,
    String maskedNumber,    // last 4 digits only — full number is encrypted
    CardType cardType,
    CardStatus status,
    LocalDate expiryDate,
    String accountCode,
    LocalDateTime createdAt
) {}

// CardUpdateDto.java
public record CardUpdateDto(
    CardStatus status,   // null = no change
    String pin           // null = no change
) {}
```

### DTO with Nested Validation

```java
public record EnvelopeRequestDto(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Positive BigDecimal targetAmount,
    @NotBlank @Size(max = 3) String currency,
    @NotNull UUID accountId,
    @Future LocalDate targetDate            // must be in the future
) {
    public EnvelopeRequestDto {
        if (name.isBlank()) throw new IllegalArgumentException("Name cannot be blank");
        // targetDate null check not needed — @NotNull handles it
    }
}
```

### Where NOT to Use Records

| Case | Use Instead |
|---|---|
| JPA entity | `@Entity` class with Lombok `@Getter @Builder @NoArgsConstructor @AllArgsConstructor` |
| Spring `@Configuration` | Regular class |
| Service/component with state | Regular class with `@RequiredArgsConstructor` |
| Exception | Class extending `BankingException` |

---

## Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| `@Data class AccountRequestDto` | Mutable, generates setters, equals/hashCode on mutable state | Use `record` |
| Calling `dto.setName(...)` after construction | Records are immutable — won't compile | Redesign: create new record or use `@MappingTarget` in mapper |
| `@Valid` on record field type | Must be `@Valid` on the controller parameter, not inside the record | `@Valid @RequestBody AccountRequestDto dto` in controller |
| `Optional<String>` as record component | Jakarta validation doesn't work on Optional components | Use nullable component + `@NotNull` if required |
| `Map<String, Object>` as DTO | Untyped, not validated, not documented | Create a typed record |

---

## Resources

- `skill-hexagonal-architecture.md` — Where DTOs live in the package structure
- `skill-java-exception-handling.md` — Exception pattern (NOT records)
- `skill-junit5-testing-patterns.md` — How to test record validation
- Jakarta Validation docs: https://jakarta.ee/specifications/bean-validation/3.0/
- MapStruct docs: https://mapstruct.org/documentation/stable/reference/html/
