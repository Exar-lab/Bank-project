# Skill Navigation Index — banco-service

**Purpose**: Central navigation guide for developers and AI agents. When you need to implement something, search this index to find which skill(s) to read.

---

## Quick Navigation by Role

### 👤 I'm a Developer Building a Feature

**Start here**: Choose your scenario below, find the skills to read, then implement.

### 🤖 I'm an AI Agent (or Copilot/Claude)

**Use this index**: Before writing ANY code:
1. Find the scenario/action in the table below
2. Load all skills listed
3. Review examples (✅ correct patterns + ❌ mistakes to avoid)
4. Follow proyecto conventions (com.banco.co.*, Records, Optional, constructor injection)

---

## Scenario-to-Skills Mapping

This is the **SOURCE OF TRUTH** for what skills to reference for each task.

### Domain Layer Scenarios

| Scenario | What You're Doing | Skills to Read | Project Package | Notes |
|----------|------------------|-----------------|-----------------|-------|
| Create a new Account model | Define entity with validations, relationships | Read: `hexagonal-architecture` (domain layer concept), `java-records-dtos` (entity design) | com.banco.co.account.model | Use Lombok, abstract classes for hierarchy |
| Add business logic to Account | Implement transfer rules, balance checks | Read: `hexagonal-architecture` (domain services) | com.banco.co.account.service | Keep pure Java, no Spring annotations |
| Handle domain exceptions | Create abstract exception hierarchy | Read: `java-exception-handling`, `hexagonal-architecture` | com.banco.co.exception | Use abstract classes for type safety |

### Application Layer Scenarios

| Scenario | What You're Doing | Skills to Read | Project Package | Notes |
|----------|------------------|-----------------|-----------------|-------|
| Create a request DTO | Map incoming JSON to immutable data | Read: `java-records-dtos`, `spring-boot-validation` | com.banco.co.account.dto | Use Records (not classes), validate in controller |
| Create a response DTO | Map entity to outgoing JSON | Read: `java-records-dtos`, `spring-boot-mapstruct-dtos` | com.banco.co.account.dto | Use Records with MapStruct for complex mappings |
| Implement a use case/service | Business logic orchestration | Read: `hexagonal-architecture` (application layer), `java-optional-handling`, `java-dependency-injection` | com.banco.co.account.application | Constructor inject all dependencies, return Optional |
| Map entity to DTO | Transform Account → AccountResponseDto | Read: `spring-boot-mapstruct-dtos` | com.banco.co.account.mapper | Use @Mapper, handle nested collections |

### Infrastructure Layer Scenarios

| Scenario | What You're Doing | Skills to Read | Project Package | Notes |
|----------|------------------|-----------------|-----------------|-------|
| Query database for accounts | Custom JPA queries, pagination, sorting | Read: `spring-data-jpa-repositories`, `java-optional-handling` | com.banco.co.account.repository | Use @Query, JOIN FETCH for N+1 prevention |
| Save/update in database | Persist entity changes | Read: `spring-data-jpa-repositories`, `hexagonal-architecture` (infrastructure layer) | com.banco.co.account.repository | Use repository interface, @Transactional at service |
| Implement security | JWT, role checks, CORS | Read: `spring-security-jwt`, `java-dependency-injection` | com.banco.co.security | Use SecurityFilterChain, no hardcoded secrets |
| Publish a domain event to Kafka | Transactional outbox + KafkaTemplate send | Read: `skill-kafka-async-messaging` | com.banco.co.infrastructure.kafka | Use outbox pattern — never dual-write |
| Create an outbox entry after domain operation | Persist event atomically with domain entity | Read: `skill-kafka-async-messaging`, `spring-data-jpa-repositories` | com.banco.co.infrastructure.persistence | Same @Transactional block as domain save |
| Poll outbox and batch-publish events | OutboxScheduler with @Scheduled | Read: `skill-kafka-async-messaging` | com.banco.co.infrastructure.scheduler | fixedDelay not fixedRate; per-entry try/catch |
| Define a domain event DTO | Immutable event payload Record | Read: `skill-kafka-async-messaging`, `java-records-dtos` | com.banco.co.{feature}.event | Nested Payload record; eventId for idempotency |
| Configure Kafka topics and producer | KafkaTemplate, ProducerFactory, NewTopic beans | Read: `skill-kafka-async-messaging` | com.banco.co.infrastructure.kafka | acks=all, idempotence=true, 3 partitions |
| Consume Kafka events (@KafkaListener) | Event listener with manual ack + DLT | Read: `skill-kafka-async-messaging` | com.banco.co.{feature}.listener | Always ack duplicates; check alreadyProcessed |
| Handle Kafka consumer failures | Dead Letter Topic routing | Read: `skill-kafka-async-messaging` | com.banco.co.infrastructure.kafka | DefaultErrorHandler + ExponentialBackOff + DLT |

### Presentation Layer Scenarios

| Scenario | What You're Doing | Skills to Read | Project Package | Notes |
|----------|------------------|-----------------|-----------------|-------|
| Create a REST endpoint | @RestController, handle requests | Read: `spring-boot-controllers`, `spring-boot-validation`, `java-dependency-injection` | com.banco.co.account.controller | Validate input with @Valid, inject service |
| Validate incoming request | Check DTO fields are correct | Read: `spring-boot-validation`, `java-records-dtos` | (in DTO) | Use @Valid in controller, custom @Constraint |
| Handle API errors | Map exceptions to HTTP responses | Read: `spring-boot-exception-handler`, `java-exception-handling` | com.banco.co.config | Use GlobalExceptionHandler, return proper status codes |

---

## Feature Development Workflow

### If you're adding a NEW FEATURE (e.g., "Add Card Management")

**Step 1: Planning** (use SDD)
- Read: `hexagonal-architecture` → understand layers
- Read: `conventional-commits` → know how to commit

**Step 2: Domain Layer**
- Read: `java-records-dtos` (entity design)
- Read: `java-exception-handling` (custom exceptions)
- Read: `hexagonal-architecture` (domain services concept)

**Step 3: Application Layer**
- Read: `java-records-dtos` (DTOs as Records)
- Read: `spring-boot-mapstruct-dtos` (if complex mappings)
- Read: `java-optional-handling` (return Optional from services)
- Read: `java-dependency-injection` (constructor injection)

**Step 4: Infrastructure Layer**
- Read: `spring-data-jpa-repositories` (JPA queries, @Repository)
- Read: `spring-security-jwt` (if access control needed)

**Step 5: Presentation Layer**
- Read: `spring-boot-validation` (request validation)
- Read: `spring-boot-controllers` (REST endpoints)
- Read: `spring-boot-exception-handler` (error responses)

**Step 6: Testing**
- Read: `spring-boot-testing-junit5-complete` (all test types)
- Coverage targets: Domain 90%+, App 85%+, Infra 70%+

---

## Debugging & Fixing Workflow

### If you have a BUG (e.g., "N+1 queries on account listing")

1. **Identify the layer**: Where's the bug? (DB query? Mapping? Controller?)
2. **Find relevant skill**:
   - Query issue? → `spring-data-jpa-repositories`
   - Mapping issue? → `spring-boot-mapstruct-dtos`
   - Security issue? → `spring-security-jwt`
   - Validation issue? → `spring-boot-validation`
   - Exception issue? → `java-exception-handling`
3. **Review ❌ examples**: "How would this fail?"
4. **Apply fix**: Follow ✅ correct pattern

---

## Refactoring Workflow

### If you're REFACTORING (e.g., "Optimize TransactionService")

1. **Read**: `hexagonal-architecture` (ensure layer separation maintained)
2. **Read**: Related skill for the layer (e.g., `spring-data-jpa-repositories` for persistence optimization)
3. **Read**: `spring-boot-testing-junit5-complete` (ensure tests pass before + after)
4. **Verify**: No behavior changes, only performance/maintainability improvements

---

## Code Review Checklist

When reviewing code, use this to know which skills to reference:

### ✅ Domain Layer Check
- [ ] Business logic is pure Java (no Spring)
- [ ] Exceptions are abstract classes (not generic Exception)
- [ ] Read: `java-exception-handling`, `hexagonal-architecture`

### ✅ Application Layer Check
- [ ] Services use constructor injection (no @Autowired)
- [ ] DTOs are Records (not @Data classes)
- [ ] Methods return Optional (no null returns)
- [ ] Read: `java-dependency-injection`, `java-records-dtos`, `java-optional-handling`

### ✅ Infrastructure Layer Check
- [ ] Repositories use @Query with JOIN FETCH (N+1 prevention)
- [ ] DTO projection used for large queries
- [ ] No business logic in repository
- [ ] Read: `spring-data-jpa-repositories`, `spring-boot-mapstruct-dtos`

### ✅ Presentation Layer Check
- [ ] Input validation with @Valid (not in service)
- [ ] Global exception handler for errors
- [ ] Security filters applied (@PreAuthorize, CORS)
   - [ ] Read: `spring-boot-validation`, `spring-boot-exception-handler`, `spring-security-jwt`

### ✅ Testing Check
- [ ] Domain tests: 90%+ coverage
- [ ] Application tests: 85%+ coverage
- [ ] Infrastructure tests: 70%+ coverage
- [ ] Read: `spring-boot-testing-junit5-complete`

---

## Skill Directory

All project skills live in `.claude/skills/`. Navigation files live in `.claude/navigation/`. Global SDD skills are in `~/.config/Claude/skills/`.

### Project-Level Skills (`.claude/skills/`)

| Skill | Purpose | For Who | When |
|-------|---------|---------|------|
| `spring-data-jpa-repositories.md` | JPA queries, pagination, N+1 prevention | Backend Dev, AI | Writing repository methods |
| `spring-boot-mapstruct-dtos.md` | DTO mapping, nested objects | Backend Dev, AI | Creating mappers |
| `spring-security-jwt.md` | JWT, SecurityFilterChain, RBAC, CORS | Backend Dev, AI | Implementing security |
| `spring-boot-validation.md` | Request validation | Backend Dev, AI | Creating DTOs/controllers |
| `spring-boot-testing-junit5-complete.md` | Unit/integration tests | Backend Dev, AI | Writing tests |
| `hexagonal-architecture.md` | Layer separation, package structure | All agents | Any architecture question |
| `java-records-dtos.md` | Records as DTOs | Domain/Application agent | Creating value objects |
| `java-optional-handling.md` | Optional patterns | Application agent | Null-safe service methods |
| `java-dependency-injection.md` | Constructor injection | All Build agents | Wiring Spring beans |
| `java-exception-handling.md` | Abstract exception hierarchies | Domain agent | Custom exceptions |
| `junit5-testing-patterns.md` | JUnit 5 patterns | Test agent | Writing tests |
| `conventional-commits.md` | Commit message format | All agents | Before committing |
| `skill-kafka-async-messaging.md` | Kafka outbox pattern, KafkaTemplate, @KafkaListener, DLT | Infrastructure agent | Publishing/consuming events |

### Navigation Files (`.claude/navigation/`)

| File | Purpose |
|------|---------|
| `skill-registry.md` | Scenario-based skill selection with scenario→skill mapping |
| `SKILL_NAVIGATION_INDEX.md` | This file — central navigation guide |
| `SKILL_AUDIT_CHECKLIST.md` | Quality checklist for skill registry |

### Global Skills (SDD Lifecycle)

| Skill | Purpose | When |
|-------|---------|------|
| `sdd-init` | Initialize project context | First time setup |
| `sdd-explore` | Investigate feature ideas | Planning phase |
| `sdd-propose` | Create change proposal | Before implementation |
| `sdd-spec` | Write detailed specs | Defining requirements |
| `sdd-design` | Technical architecture design | Complex features |
| `sdd-tasks` | Break into implementation tasks | Task breakdown |
| `sdd-apply` | Implement code following specs | Implementation phase |
| `sdd-verify` | Validate against specs | Before merge |
| `sdd-archive` | Mark change complete | After verify passes |

---

## AI Agent Instructions

If you're an AI agent working on banco-service:

1. **Before writing code**: Read this navigation index
2. **Find your scenario**: Use the table above (Scenario → Skills)
3. **Load the skill(s)**: Read the exact files listed
4. **Review examples**: Both ✅ correct AND ❌ wrong patterns
5. **Follow conventions**: 
   - Packages: `com.banco.co.*`
   - DTOs: Use Records (not @Data)
   - Injection: Constructor only (no @Autowired fields)
   - Nulls: Use Optional (never .get() without isPresent())
   - Exceptions: Abstract classes
   - Mapping: Use MapStruct (@Mapper)
6. **Code with confidence**: All skills are project-specific, tested, and verified

---

## Examples: How to Use This Index

### Example 1: "I need to create an Account controller endpoint"

1. Find scenario: "Create a REST endpoint"
2. Skills: `spring-boot-controllers`, `spring-boot-validation`, `java-dependency-injection`
3. Load each skill:
   - `spring-boot-controllers` → shows @RestController, @PostMapping patterns
   - `spring-boot-validation` → shows @Valid usage
   - `java-dependency-injection` → shows constructor injection
4. Review examples (✅ how to do it right, ❌ common mistakes)
5. Write code following patterns

### Example 2: "I need to query accounts with pagination"

1. Find scenario: "Query database for accounts"
2. Skills: `spring-data-jpa-repositories`, `java-optional-handling`
3. Load each skill:
   - `spring-data-jpa-repositories` → shows @Query, JOIN FETCH, pagination
   - `java-optional-handling` → shows Optional return types
4. Review ❌ example: N+1 problem (lazy loading in loop)
5. Write repository using JOIN FETCH pattern

### Example 3: "I need to test my repository"

1. Find scenario: (in Testing section) "Write tests for repository"
2. Skills: `spring-boot-testing-junit5-complete`
3. Load skill:
   - Shows @DataJpaTest, @Sql, assertions, test data setup
4. Review examples
5. Write tests with proper isolation

---

## Maintenance

This index is maintained alongside skills. When adding a new skill:

1. Add row to appropriate scenario table (Domain/App/Infra/Presentation)
2. Update skill directory section
3. Update code review checklist if relevant

**Version**: 2026-03-13  
**Last Updated**: After skill-registry-completion SDD change  
**Maintained By**: Architecture team
