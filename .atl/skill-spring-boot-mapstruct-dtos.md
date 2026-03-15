---
name: spring-boot-mapstruct-dtos
description: "MapStruct DTO mapping patterns: entity-to-DTO conversion, nested objects, custom field transformations"
trigger: "When mapping between JPA entities and DTOs or vice versa"
layer: infrastructure
tags:
  - mapping
  - dto
  - mapstruct
  - transformation
---

# Skill: spring-boot-mapstruct-dtos

## When to Use

**Trigger Context**:
- Converting JPA entities to DTOs for REST responses
- Converting request DTOs to entities for persistence
- Mapping nested objects (Account + related Transactions)
- Custom field transformations during mapping (e.g., enum to string)
- Partial updates with null-safe field mapping

**Decision Tree**:
- Need simple 1:1 entity↔DTO mapping? → Use `@Mapper` with `@Mapping` for field transforms
- Need nested collection mapping? → Use `@Mapper` with nested mapper methods
- Need custom transformation? → Use `@Mapping(qualifiedByName)` or custom mapping method
- Need to ignore fields? → Use `@Mapping(target="field", ignore=true)`
- Need to skip null values? → Use `@BeanMapping(nullValuePropertyMappingStrategy=IGNORE)`

---

## Critical Patterns

### ✅ Correct: Basic @Mapper with Field Transformation

```java
package com.banco.co.account.mapper;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IAccountMapper {
    
    // Maps user.email to userEmail field
    @Mapping(source = "user.email", target = "userEmail")
    AccountResponseDto toDto(Account account);
    
    // Reverse mapping with ignored fields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountCode", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Account toEntity(AccountRequestDto dto);
}
```

**Why this works**:
- `@Mapper(componentModel = "spring")` registers as Spring bean
- `@Mapping(source, target)` extracts nested fields (user.email → userEmail)
- `ignore = true` prevents overwriting read-only fields
- MapStruct generates implementation at compile time
- Type-safe: no runtime reflection

---

### ✅ Correct: Nested Collections Mapping

```java
public record AccountResponseDto(
    UUID id,
    String accountNumber,
    BigDecimal balance,
    String userEmail,
    List<TransactionDto> transactions  // Nested list
) {}

@Mapper(componentModel = "spring", uses = TransactionMapper.class)
public interface IAccountMapper {
    
    @Mapping(source = "user.email", target = "userEmail")
    AccountResponseDto toDto(Account account);
    
    // Nested mapper for Transaction → TransactionDto
    TransactionDto transactionToDto(Transaction transaction);
}

public record TransactionDto(
    UUID id,
    BigDecimal amount,
    String type
) {}

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionDto toDto(Transaction transaction);
}
```

**Why this works**:
- `uses = TransactionMapper.class` tells MapStruct how to map nested Transaction objects
- Collections (List, Set) are automatically iterated
- Each Transaction element is mapped via transactionToDto method
- No manual loop or stream needed

---

### ✅ Correct: Partial Updates with Null Safety

```java
@Mapper(componentModel = "spring")
public interface IAccountMapper {
    
    // Standard mapping
    @Mapping(source = "user.email", target = "userEmail")
    AccountResponseDto toDto(Account account);
    
    // Partial update: only non-null fields from DTO are applied to entity
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountCode", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDto(AccountUpdateDto dto, @MappingTarget Account account);
}
```

**Why this works**:
- `nullValuePropertyMappingStrategy = IGNORE` skips null fields from DTO
- Allows partial updates: only provided fields are updated
- `@MappingTarget` specifies which parameter to update
- Original account keeps its values for null fields in DTO

---

### ❌ Wrong: Manual Field Mapping in Service (No Mapper)

```java
@Service
public class AccountService {
    private final IAccountRepository accountRepository;
    
    public AccountResponseDto getAccount(UUID id) {
        Account account = accountRepository.findById(id).orElseThrow();
        
        // WRONG: Manual field-by-field copy (error-prone, duplicate code)
        AccountResponseDto dto = new AccountResponseDto(
            account.getId(),
            account.getAccountNumber(),
            account.getBalance(),
            account.getUser().getEmail(),  // Manual nested access
            // What if user is null? NullPointerException!
            account.getTransactions()  // Wrong type! Need List<TransactionDto>
        );
        
        return dto;
    }
}
```

**Why this fails**:
- Manual mapping is tedious and error-prone
- Easy to forget fields or use wrong types
- No null-safety: accessing `getUser().getEmail()` crashes if user is null
- Duplicate code in multiple services
- Hard to maintain: if DTO changes, must update all services

**Fix**: Use MapStruct:
```java
@Service
public class AccountService {
    private final IAccountRepository accountRepository;
    private final IAccountMapper accountMapper;
    
    public AccountResponseDto getAccount(UUID id) {
        Account account = accountRepository.findById(id).orElseThrow();
        return accountMapper.toDto(account);  // One-liner, null-safe
    }
}
```

---

### ❌ Wrong: Circular Reference in Nested Mapping

```java
public record AccountDto(
    UUID id,
    String accountNumber,
    UserDto user  // Nested user
) {}

public record UserDto(
    UUID id,
    String email,
    List<AccountDto> accounts  // Circular: User → Account → User → ...
) {}

@Mapper(componentModel = "spring")
public interface IAccountMapper {
    AccountDto toDto(Account account);
    UserDto userToDto(User user);
}
```

**Why this fails**:
- Circular reference causes infinite recursion
- StackOverflowError at runtime
- User maps to Account which maps back to User infinitely
- MapStruct cannot detect cycles

**Fix**: Use `ignore = true` to break the cycle:
```java
@Mapper(componentModel = "spring")
public interface IAccountMapper {
    
    @Mapping(source = "user", target = "user")
    AccountDto toDto(Account account);
    
    @Mapping(target = "accounts", ignore = true)  // Break cycle
    UserDto userToDto(User user);
}
```

---

### ✅ Correct: Enum Mapping with Custom Transformation

```java
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}

public record AccountResponseDto(
    UUID id,
    String accountNumber,
    String status  // String, not enum
) {}

@Mapper(componentModel = "spring")
public interface IAccountMapper {
    
    @Mapping(source = "status", target = "status")  // Explicit enum → string
    AccountResponseDto toDto(Account account);
    
    // Custom method for complex transformations
    default String mapStatus(AccountStatus status) {
        return status == null ? "UNKNOWN" : status.name();
    }
}
```

**Why this works**:
- MapStruct can convert between enums and strings
- Custom `default` method handles special logic (null → "UNKNOWN")
- Reusable across multiple mappings
- Type-safe enum handling

---

## Important: MapStruct + Lombok Annotation Processing Order

⚠️ **CRITICAL**: MapStruct processor must run AFTER Lombok processor

In `pom.xml`:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPath>
            <!-- Lombok FIRST -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>1.18.30</version>
            </path>
            <!-- MapStruct SECOND -->
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.6.3</version>
            </path>
        </annotationProcessorPath>
    </configuration>
</plugin>
```

---

## References

- Related: [spring-data-jpa-repositories](./skill-spring-data-jpa-repositories.md) — Querying entities from database
- Related: [spring-boot-validation](./skill-spring-boot-validation.md) — Validating DTOs before mapping
- External: [MapStruct Documentation](https://mapstruct.org/documentation/)
- External: [MapStruct Nested Collections Guide](https://mapstruct.org/documentation/stable/reference/html/)
