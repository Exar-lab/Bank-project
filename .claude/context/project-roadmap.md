# Banco Service — Project Roadmap

## Overview

**banco-service** is a banking backend built with Spring Boot 4.x and Java 21/24. It implements a feature-first
("Screaming Architecture") layout where each business domain drives the package structure, rather than technical layers.

---

## Stack (verified from `pom.xml`)

| Concern | Technology |
|---|---|
| Runtime | Java 21 (declared), compiled Java 24 + `--enable-preview` |
| Framework | Spring Boot **4.0.2** |
| Database | **MySQL** (via Spring Data JPA + Hibernate) |
| JWT | `com.auth0:java-jwt:4.5.0` — HMAC256, custom filter |
| Mapping | MapStruct 1.6.3 |
| Validation | Spring Validation (Jakarta) |
| Security | Spring Security 6.x, `@EnableMethodSecurity` |
| Encryption | Jasypt 4.0.4 (JPA AttributeConverter for sensitive fields) |
| Boilerplate | Lombok |
| Testing | JUnit 5, Mockito, Testcontainers |
| Build | Maven |

---

## Architecture: Screaming (Feature-First)

All code lives under `com.banco.co.{feature}.*`. The feature name (not the layer) is the top-level package.

```
com/banco/co/
├── account/
│   ├── model/          # Entity (JPA), Value Objects — NO Spring
│   ├── enums/          # AccountType, AccountStatus, Currency
│   ├── exception/      # AccountException hierarchy (abstract classes)
│   ├── dto/            # Records: AccountRequestDto, AccountResponseDto, AccountUpdateDto
│   ├── mapper/         # IAccountMapper (MapStruct)
│   ├── repository/     # IAccountRepository extends JpaRepository
│   ├── service/        # IAccountService + AccountService (@Service)
│   └── controller/     # (stub / not yet implemented)
│
├── transaction/        # Same sub-package layout
├── envelope/           # Envelope = budget bucket linked to Account
├── user/               # UserCredential entity, user management
├── card/               # Debit/credit card management
├── role/               # RBAC roles
├── permission/         # Fine-grained permissions (account:read, etc.)
├── auditLog/           # Audit trail for all mutations
├── fraud/              # Fraud detection rules
├── security/           # JwtUtils, JwtTokenValidator, SecurityUser, HashUtils, JasyptEncryptor
├── exception/          # Global BankingException hierarchy (abstract base classes)
└── utils/              # Shared utilities
```

### Layer Responsibilities

| Layer | Location | Rules |
|---|---|---|
| **Domain** | `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/` | No Spring, no JPA annotations allowed in pure logic. Business methods on entities (rich domain model). |
| **Application** | `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/` | Orchestrates domain, uses constructor injection, uses MapStruct DTOs. |
| **Infrastructure** | `{feature}/repository/`, `security/` config classes | JPA repositories, security filters, JasyptEncryptor converters. |
| **Presentation** | `{feature}/controller/`, `{feature}/handler/` | REST endpoints (not yet implemented in most features). |

---

## Features Status

| Feature | Model | Repository | Service | DTO | Mapper | Controller | Tests |
|---|---|---|---|---|---|---|---|
| `account` | ✅ complete | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `transaction` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `envelope` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `user` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `card` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `role` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `permission` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ stub | ❌ |
| `auditLog` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `fraud` | ✅ | partial | partial | partial | partial | ❌ | ❌ |
| `security` | ✅ JwtUtils | — | — | — | — | — | ❌ |
| `exception` | ✅ hierarchy | — | — | — | — | — | — |

---

## Key Patterns

### 1. Rich Domain Model
Business logic lives inside entities, not in anemic service classes:
```java
account.deposit(amount);       // validates + mutates
account.withdraw(amount);      // throws InsufficientFundsException
envelope.allocate(amount);     // budget allocation logic
card.block();                  // sets status + audit reason
```

### 2. Records as DTOs
Every DTO is a Java record. Jakarta validation annotations go directly on record components.
Cross-field validation uses the compact constructor.

### 3. Abstract Exception Hierarchy
`BankingException (abstract) → feature-specific abstract → concrete leaf`.
Leaf exceptions carry `errorCode`, `httpStatus`, and a metadata map.
**NOT sealed** — abstract classes with explicit extends chain.

### 4. Constructor Injection via @RequiredArgsConstructor
All Spring beans use `@RequiredArgsConstructor` (Lombok) or explicit constructors. Zero `@Autowired` on fields.

### 5. Optional Without .get()
All repositories return `Optional<T>`. Services chain `.orElseThrow()` with a domain exception. Never `.get()` bare.

### 6. MapStruct with Null-Safe Updates
Update DTOs use `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` so partial updates don't wipe fields.

### 7. JWT via Auth0 Library
No Spring OAuth2 Resource Server. Custom `JwtTokenValidator extends OncePerRequestFilter` validates tokens and
loads `SecurityUser` (implements `UserDetails`) into the `SecurityContextHolder`.

---

## Known Gaps & Tech Debt

| Gap | Severity | Notes |
|---|---|---|
| **No REST controllers** | HIGH | All features lack `@RestController` implementations. Presentation layer is a stub. |
| **No tests** | HIGH | Zero test files. Coverage = 0% across all layers. |
| **No `@Transactional(readOnly=true)`** on queries | MEDIUM | `@Transactional` used on mutations. Read-only flag missing — known gap. |
| **JWT filter not fully wired** | MEDIUM | `JwtTokenValidator` exists but full integration with all endpoints is incomplete. |
| **Fraud detection** partial | MEDIUM | `fraud/` feature has model but service logic is incomplete. |
| **No global exception handler** | HIGH | No `@RestControllerAdvice` / `@ExceptionHandler` translating domain exceptions to HTTP responses yet. |
| **No integration tests** | HIGH | Testcontainers config not set up. |
| **Kafka not implemented** | LOW/FUTURE | Async event publishing is in roadmap but not in codebase. |

---

## Environment Variables

```properties
# Datasource
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/banco_service
SPRING_DATASOURCE_USERNAME=banco_user
SPRING_DATASOURCE_PASSWORD=<encrypted via Jasypt>

# JWT
JWT_SECRET=<HMAC256 secret key>
JWT_ACCESS_EXPIRY_MINUTES=15
JWT_REFRESH_EXPIRY_DAYS=30

# Jasypt (field-level encryption)
JASYPT_ENCRYPTOR_PASSWORD=<master password>

# BCrypt
BCRYPT_STRENGTH=12
```

---

## Multi-Agent Context

This project uses a 7-agent team for parallel development:

| Agent | Role | Touches |
|---|---|---|
| **Planning** | SDD orchestration, proposals, specs, design | `.claude/`, `.atl/` docs |
| **Build-Domain** | Entities, enums, domain exceptions | `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/` |
| **Build-Application** | Services, DTOs, mappers | `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/` |
| **Build-Infrastructure** | Repositories, security config | `{feature}/repository/`, `security/` |
| **Build-Presentation** | REST controllers, handlers | `{feature}/controller/`, `{feature}/handler/` |
| **QA** | Unit + integration tests | `src/test/` |
| **Security** | JWT, RBAC, encryption audits | `security/`, `@PreAuthorize` annotations |

### When to use SDD
Use `/sdd-new {name}` for any feature that spans more than 2 hours or touches more than 2 layers:
```bash
/sdd-new account-rest-controllers
/sdd-new test-coverage-account
/sdd-new global-exception-handler
```

---

## Roadmap Milestones

### M1 — Presentation Layer (current priority)
- [ ] Global `@RestControllerAdvice` with `BankingException` → HTTP translation
- [ ] `AccountController` REST endpoints (CRUD + transfer)
- [ ] `TransactionController` REST endpoints
- [ ] `AuthController` (login, refresh token, logout)
- [ ] `UserController` (register, profile)
- [ ] `EnvelopeController` (budget management)

### M2 — Test Coverage
- [ ] Domain layer tests (target 90%) — JUnit 5, no mocks
- [ ] Application layer tests (target 85%) — Mockito
- [ ] Infrastructure layer tests (target 70%) — Testcontainers + MySQL
- [ ] Presentation layer tests (target 75%) — `@WebMvcTest`

### M3 — Hardening
- [ ] `@Transactional(readOnly = true)` on all read-only queries
- [ ] Complete JWT filter integration for all secured endpoints
- [ ] OpenAPI 3 / Swagger documentation
- [ ] Rate limiting on auth endpoints

### M4 — Event-Driven (future)
- [ ] Kafka integration (OutboxPattern)
- [ ] `skill-kafka-async-messaging.md` skill
- [ ] Async audit log publishing
