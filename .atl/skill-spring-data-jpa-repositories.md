---
name: spring-data-jpa-repositories
description: "Spring Data JPA repository patterns, custom queries, pagination, and performance optimization"
trigger: "When creating or querying JPA repositories in banco-service"
layer: infrastructure
tags:
  - persistence
  - jpa
  - queries
  - performance
---

# Skill: spring-data-jpa-repositories

## When to Use

**Trigger Context**:
- Querying database with filters, pagination, sorting
- Writing custom repository methods with @Query
- Optimizing N+1 query problems
- Implementing repository inheritance patterns
- DTO projection for memory efficiency

**Decision Tree**:
- Need simple CRUD? → Use `JpaRepository<T, ID>`
- Need custom queries? → Use `@Query` with JPQL or native SQL
- Need complex filters? → Use `Specification<T>` or custom @Query methods
- Need to avoid loading all fields? → Use DTO projection in @Query
- Need eager loading? → Use `@EntityGraph` or JOIN FETCH in @Query

---

## Critical Patterns

### ✅ Correct: Custom @Query with JOIN FETCH to Prevent N+1

```java
package com.banco.co.account.repository;

import com.banco.co.account.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID> {
    
    // JOIN FETCH ensures user is loaded in single query
    @Query("""
        SELECT a FROM Account a 
        LEFT JOIN FETCH a.user u 
        WHERE a.accountCode = :accountCode
    """)
    Optional<Account> findAccountWithUser(@Param("accountCode") String accountCode);
}
```

**Why this works**:
- `LEFT JOIN FETCH` loads related entities in one query (prevents N+1)
- JPQL is database-agnostic
- Optional provides safe null handling
- Named parameters prevent SQL injection

---

### ✅ Correct: Using Method Naming Convention for Simple Queries

```java
@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID> {
    
    // Spring generates query from method name
    Optional<Account> findFirstActiveAccountByUser_Email(String userEmail);
    
    List<Account> findActiveAccountsByUser_Email(String userEmail);
    
    boolean existsByUser_EmailAndAccountType(String userEmail, AccountType accountType);
}
```

**Why this works**:
- Spring Data automatically generates queries from method names
- Underscore (`_`) navigates relationships (User_Email = User.email)
- No @Query needed for simple cases
- Readable and self-documenting

---

### ✅ Correct: DTO Projection to Reduce Memory Footprint

```java
public record AccountSummaryDto(
    UUID id,
    String accountNumber,
    BigDecimal balance
) {}

@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID> {
    
    @Query("""
        SELECT new com.banco.co.account.dto.AccountSummaryDto(
            a.id, a.accountNumber, a.balance
        )
        FROM Account a
        WHERE a.user.id = :userId
        ORDER BY a.createdAt DESC
    """)
    List<AccountSummaryDto> findAccountsByUserId(@Param("userId") Long userId);
}
```

**Why this works**:
- `new` constructor projects only needed fields
- Reduces memory when fetching large lists
- DTO is immutable record (no risk of accidental mutations)
- Query returns lightweight DTOs, not full entities

---

### ❌ Wrong: N+1 Query Problem (Lazy Loading in Loop)

```java
@Service
public class AccountService {
    private final IAccountRepository accountRepository;
    
    public List<AccountResponseDto> getAllAccountsWithTransactions() {
        List<Account> accounts = accountRepository.findAll(); // Query 1
        
        accounts.forEach(account -> {
            // Lazy loading triggers SELECT for EACH account
            List<Transaction> txns = account.getTransactions(); // Queries 2...N+1
            // Now accessing account.getUser() triggers MORE queries
            User user = account.getUser();
        });
        
        return accounts.stream().map(this::toDto).collect(toList());
    }
}
```

**Why this fails**:
- `findAll()` loads Account entities, but relationships are lazy
- Accessing `getTransactions()` for each account triggers N separate queries
- Accessing `getUser()` triggers N more queries
- **Total: 1 + N + N = 2N+1 queries** (scales exponentially)
- Massive performance problem for large datasets

**Fix**: Use `@EntityGraph` or JOIN FETCH in @Query:
```java
@EntityGraph(attributePaths = {"transactions", "user"})
List<Account> findAll();
```

---

### ❌ Wrong: Loading Entire Table Without Pagination

```java
@Service
public class ReportService {
    private final IAccountRepository accountRepository;
    
    public List<Account> generateReport() {
        // WRONG: Loads ALL accounts into memory
        List<Account> allAccounts = accountRepository.findAll();
        
        // If database has 1M+ accounts, OutOfMemoryError likely
        return allAccounts;
    }
}
```

**Why this fails**:
- `findAll()` without pagination loads entire table into memory
- Causes OutOfMemoryError on large datasets
- No limits, sorting, or filtering applied
- Not suitable for production APIs

**Fix**: Use Pagination with `Pageable`:
```java
public interface IAccountRepository extends JpaRepository<Account, UUID>, PagingAndSortingRepository<Account, UUID> {
    
    @Query("SELECT a FROM Account a WHERE a.status = :status")
    Page<Account> findByStatus(@Param("status") String status, Pageable pageable);
}

// In service:
Page<Account> page = accountRepository.findByStatus("ACTIVE", 
    PageRequest.of(0, 20, Sort.by("createdAt").descending())
);
```

---

### ✅ Correct: Pagination with Sorting

```java
@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID>, 
    PagingAndSortingRepository<Account, UUID> {
    
    @Query("SELECT a FROM Account a WHERE a.status = :status")
    Page<Account> findByStatus(@Param("status") String status, Pageable pageable);
}

@Service
public class AccountService {
    private final IAccountRepository accountRepository;
    
    public Page<AccountResponseDto> listAccounts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return accountRepository.findByStatus("ACTIVE", pageable)
            .map(account -> mapper.toDto(account));
    }
}
```

**Why this works**:
- `Pageable` limits results (page, size) and adds sorting
- Returns `Page<T>` with metadata (totalPages, totalElements, hasNext)
- Scales to millions of records
- Client can request page 0, 1, 2, etc.

---

## References

- Related: [spring-boot-testing-junit5-complete](./skill-spring-boot-testing-junit5-complete.md) — Testing repository queries with @DataJpaTest
- Related: [spring-boot-mapstruct-dtos](./skill-spring-boot-mapstruct-dtos.md) — Mapping repository results to DTOs
- External: [Spring Data JPA Documentation](https://spring.io/projects/spring-data-jpa)
- External: [Hibernate N+1 Prevention Guide](https://en.wikibooks.org/wiki/Java_Persistence/Relationships#One-to-Many_Relationships)
