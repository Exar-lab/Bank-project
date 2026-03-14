---
name: java-records-dtos
description: >
  Use Records for immutable DTOs and Value Objects in Java 21+.
  Trigger: When creating DTOs, request/response objects, or Value Objects.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [java21, records, immutability, dtos]
---

## Pattern: Records for DTOs

Use Records instead of @Data classes:

```java
// ✅ CORRECT - Record
public record CreateAccountRequest(
    @NotBlank(message = "Holder cannot be blank")
    String accountHolder,
    
    @NotNull(message = "Type cannot be null")
    String accountType,
    
    @DecimalMin("0.01")
    BigDecimal initialBalance
) {}

// ❌ WRONG - Mutable class
@Data
public class CreateAccountRequest {
    private String accountHolder;
    // setter hell...
}
```

## Key Points

1. **Records are immutable by default**
2. **Auto-generates equals(), hashCode(), toString()**
3. **Supports validation annotations in parameters**
4. **Compact constructors for validation logic:**

```java
public record TransferRequest(
    @NotNull UUID fromId,
    @NotNull UUID toId,
    @DecimalMin("0.01") BigDecimal amount
) {
    public TransferRequest {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Same account transfer not allowed");
        }
    }
}
```

## Use Records For

- ✅ Request/Response DTOs
- ✅ Value Objects
- ✅ API contracts
- ✅ Data transfer between layers

## Validation

Always use compact constructor for domain validation:

```java
public record Account(
    UUID id,
    String holder,
    BigDecimal balance
) {
    public Account {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(holder, "Holder cannot be null");
        Objects.requireNonNull(balance, "Balance cannot be null");
    }
}
```
