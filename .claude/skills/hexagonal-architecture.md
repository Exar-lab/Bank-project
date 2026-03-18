---
name: hexagonal-architecture
description: >
  Feature-first (Screaming) Architecture for banco-service with rich domain model and per-feature layer layout.
  Trigger: When designing new features, adding packages, or structuring modules in banco-service.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Creating a new feature package
- Adding a new class and need to decide which sub-package it belongs to
- Reviewing whether a class has the right responsibilities for its layer
- Deciding where business logic should live (spoiler: in the entity)

---

## Critical Patterns

### Feature-First Package Layout

The top-level is ALWAYS the feature name, never a layer name.

```
com/banco/co/
└── {feature}/
    ├── model/       # JPA entity + rich domain logic. NO Spring annotations in methods.
    ├── enums/       # All enums for this feature
    ├── exception/   # Feature-specific exception hierarchy (abstract → concrete)
    ├── dto/         # Records only: {Feature}RequestDto, {Feature}ResponseDto, {Feature}UpdateDto
    ├── mapper/      # MapStruct interface: I{Feature}Mapper
    ├── repository/  # Spring Data interface: I{Feature}Repository extends JpaRepository
    ├── service/     # Interface I{Feature}Service + impl {Feature}Service
    └── controller/  # @RestController (Presentation — implements last)
```

Global cross-feature concerns:
```
com/banco/co/
├── exception/   # BankingException base hierarchy (used by all features)
├── security/    # JwtTokenValidator, HashUtils (securityhasher/), JasyptEncryptor (cryptoLib/)
│   └── config/
│       └── filter/  # JwtTokenValidator
├── utils/       # JwtUtils and other shared utility classes
```

---

### Layer Responsibilities

#### Domain Layer — `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/`

- **NO Spring annotations** in business methods
- JPA annotations (`@Entity`, `@Column`) are allowed on the entity class itself — they are persistence mapping, not business logic
- Business logic MUST be in entity methods (rich domain model)
- Domain exceptions extend the feature abstract class (`AccountException`, etc.)

```java
// CORRECT — rich domain model in Account entity
@Entity
@Table(name = "accounts")
@NoArgsConstructor @AllArgsConstructor @Getter @Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    // Business logic lives HERE, not in AccountService
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Deposit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(this.balance.add(overdraftLimit)) > 0) {
            throw new InsufficientFundsException(this.id.toString(), amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void block(String reason) {
        this.status = AccountStatus.BLOCKED;
        // audit handled by caller (service delegates to auditLogService)
    }
}
```

#### Application Layer — `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/`

- Orchestrates domain objects — does NOT duplicate domain logic
- Constructor injection via `@RequiredArgsConstructor`
- Always depend on interfaces (`IAccountRepository`, not `AccountRepository` the impl)
- Maps between DTOs and entities using MapStruct mapper
- Delegates side effects (audit, notifications) to other services via their interfaces

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService implements IAccountService {
    private final IAccountRepository accountRepository;
    private final IAuditLogService auditLogService;
    private final IAccountMapper mapper;

    @Transactional
    @Override
    public AccountResponseDto deposit(String code, DepositRequestDto dto) {
        Account account = accountRepository.findActiveByCode(code)
            .orElseThrow(() -> new AccountNotFoundException(code));

        account.deposit(dto.amount());   // domain logic in entity
        Account saved = accountRepository.save(account);

        auditLogService.log(AuditEvent.DEPOSIT, saved.getId(), dto.amount());
        return mapper.toDto(saved);
    }
}
```

#### Infrastructure Layer — `{feature}/repository/`, `security/`

- Spring Data JPA repositories: `extends JpaRepository<Entity, UUID>`
- Custom `@Query` for N+1 prevention using `JOIN FETCH`
- Security config beans (`SecurityFilterChain`, filters)
- `JasyptEncryptor` as `@Converter` for encrypted fields

#### Presentation Layer — `{feature}/controller/`, `{feature}/handler/`

- `@RestController @RequestMapping("/api/v1/{feature}s")`
- `@Validated` on class for validation activation
- RBAC via `@PreAuthorize` on methods
- Return `ResponseEntity<T>` with explicit HTTP status
- NO business logic — delegate entirely to service

---

### Anti-Patterns

| Anti-pattern | Why Wrong | Fix |
|---|---|---|
| `com.banco.co.service.AccountService` (layer-first) | Breaks Screaming Architecture — feature not visible at root | `com.banco.co.account.service.AccountService` |
| Business logic in `AccountService.validateDeposit()` | Service is an orchestrator, not a rule engine | Move to `Account.deposit()` |
| `@Entity` in `{feature}/dto/` | DTOs must be Records, never JPA entities | Split into separate `model/` and `dto/` |
| `@Autowired private IAccountRepository repo;` | Field injection — not testable without Spring context | Constructor injection via `@RequiredArgsConstructor` |
| Returning `null` instead of `Optional` from repository | NPE waiting to happen | Return `Optional<Account>` always |
| Checked exceptions in domain | Pollutes call stack, forces callers to catch | Use `RuntimeException` subclasses (`BankingException` hierarchy) |
| Controller calling `repository` directly | Skips application layer, breaks separation | Controller → Service → Repository |

---

## Code Examples

### Correct Feature Layout for a New Feature (e.g., `loan`)

```
com/banco/co/loan/
├── model/
│   └── Loan.java              # @Entity with business methods
├── enums/
│   ├── LoanStatus.java
│   └── LoanType.java
├── exception/
│   ├── LoanException.java     # abstract class extends BankingException
│   └── LoanNotFoundException.java
├── dto/
│   ├── LoanRequestDto.java    # record with @NotNull etc.
│   ├── LoanResponseDto.java   # record
│   └── LoanUpdateDto.java     # record for partial updates
├── mapper/
│   └── ILoanMapper.java       # @Mapper(componentModel = "spring")
├── repository/
│   └── ILoanRepository.java   # extends JpaRepository<Loan, UUID>
├── service/
│   ├── ILoanService.java      # interface
│   └── LoanService.java       # @Service @RequiredArgsConstructor
└── controller/
    └── LoanController.java    # @RestController (implement last)
```

---

## Resources

- `skill-java-records-dtos.md` — DTO record patterns
- `skill-java-exception-handling.md` — Exception hierarchy
- `skill-java-dependency-injection.md` — Constructor injection
- `skill-spring-security-jwt.md` — Security layer
- `.claude/context/project-roadmap.md` — Full feature status
