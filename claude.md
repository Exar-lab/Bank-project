# claude.md — Multi-Agent Context for Banco-Service

**Propósito**: Brújula para agentes Claude que trabajan en distintas partes del código. Define qué hacer, cómo pensar, y cuándo leer skills.

---

## 🎯 Filosofía Multi-Agente

Este proyecto UTILIZA MULTIPLES AGENTES CLAUDE:

| Agente | Responsabilidad | Entrada | Salida |
|--------|-----------------|---------|--------|
| **Domain Agent** | Lógica de negocio, modelos, validaciones | Feature request / Bug en domain | Domain entities + unit tests |
| **Application Agent** | Use cases, mappers, orquestación | Feature request | Services + DTOs + integration tests |
| **Infrastructure Agent** | Persistencia, adapters, Kafka | Feature request | Repositories + Publishers + Testcontainers |
| **Presentation Agent** | REST endpoints, exception handlers | Feature request | Controllers + error handling + integration tests |
| **Architect Agent** | SDD workflow, decisions, design docs | Change scope / Refactoring | Specifications, designs, task breakdowns |

**CLAVE**: Cada agente es especializado y SOLO lee skills de su dominio.

---

## 📖 Cómo Leer Skills (Protocol)

### 1. Antes de Escribir Código

Tu checklist:
- [ ] ¿Qué necesito hacer? (e.g., "crear un DTO", "escribir una query")
- [ ] ¿Cuál es mi agente/dominio? (e.g., Application, Infrastructure)
- [ ] ¿Existe una skill para esto?

### 2. Encontrar la Skill Correcta

**Opción A (RECOMENDADA)**: Lee `.atl/SKILL_NAVIGATION_INDEX.md`
```
Go to: .atl/SKILL_NAVIGATION_INDEX.md
Look for: Your scenario (e.g., "Map entity to DTO")
→ Find the skill name (e.g., "spring-boot-mapstruct-dtos")
→ Reference: .atl/skill-{name}.md
```

**Opción B**: Usa el registry por layer
```
Go to: .atl/skill-registry.md
Look for: Your layer (Application, Infrastructure, etc.)
→ Find your scenario
→ Reference the linked skill
```

### 3. Dentro de la Skill

Estructura de cada skill:

```markdown
# Skill: [Name]

## When to Use
- You need to [problem]
- Use this skill when [condition]

## Critical Patterns
### ✅ Correct Pattern (Always)
[Code example - COPY THIS]

### ❌ Common Mistake (Never)
[Anti-pattern - DON'T DO THIS]

## Examples for Banco-Service
1. [Real example 1 - actual project context]
2. [Real example 2 - another layer]
3. [Common mistake to avoid]

## Best Practices
- [Banco-specific convention 1]
- [Banco-specific convention 2]

## References
- [Link to architecture doc]
- [Link to related skill]
```

**REGLA DE ORO**: Copy patterns directly from skill → Modify only data/names → Done.

---

## 🏛️ Architecture + Layering

### Layer Responsibilities

```
┌─ Presentation (REST)
│  └─ Controllers, @RestController, exception handlers
│     → OUTPUT: JSON, HTTP status codes
│     → INPUT: HTTP requests
│
├─ Application (Orchestration)
│  └─ Services, Use Cases, @Service, MapStruct mappers
│     → Calls Domain for business logic
│     → Calls Infrastructure for persistence
│     → Maps between DTOs ↔ Domain models
│
├─ Infrastructure (Adapters)
│  └─ Repositories, JPA entities, Kafka publishers
│     → Implements application ports
│     → Persists to DB
│     → Publishes events
│
└─ Domain (Pure Logic)
   └─ Value objects, Aggregates, Domain services
      → NO Spring annotations
      → NO JPA
      → NO external deps
      → 100% testable without mocks
```

**CUANDO ESCRIBAS**: Define layer PRIMERO, luego lee skill PARA ESE LAYER.

---

## ✅ Conventions You Must Follow

### 1. DTOs are ALWAYS Records
```java
// ✅ CORRECTO
public record CreateAccountDto(
    String accountHolder,
    BigDecimal initialBalance
) {}

// ❌ NUNCA
@Data
public class CreateAccountDto {
    private String accountHolder;
    // ...
}
```

### 2. Constructor Injection ONLY
```java
// ✅ CORRECTO
@Service
public class AccountService {
    private final AccountRepository repo;
    
    public AccountService(AccountRepository repo) {
        this.repo = repo;
    }
}

// ❌ NUNCA
@Service
public class AccountService {
    @Autowired
    private AccountRepository repo;
}
```

### 3. Optional (never .get())
```java
// ✅ CORRECTO
return repo.findById(id)
    .orElseThrow(() -> new AccountNotFoundException());

return repo.findById(id)
    .map(acc -> acc.getBalance())
    .orElse(BigDecimal.ZERO);

// ❌ NUNCA
return repo.findById(id).get(); // NPE waiting to happen
```

### 4. Sealed Exceptions
```java
// ✅ CORRECTO
public sealed class BancoException extends Exception
    permits InsufficientFundsException,
            AccountNotFoundException,
            TransferFailedException {}

public final class InsufficientFundsException 
    extends BancoException {}

// ❌ NUNCA
public class BancoException extends Exception {}
```

### 5. Package Naming
```
✅ CORRECTO
com.banco.co.domain.account.Account
com.banco.co.application.service.AccountService
com.banco.co.infrastructure.repository.JpaAccountRepository
com.banco.co.presentation.controller.AccountController

❌ NUNCA
com.myapp.Account
org.example.services.AccountService
```

### 6. JPA Queries (no N+1)
```java
// ✅ CORRECTO - Explicit join
@Query("SELECT new com.banco.co.application.dto.AccountDto(" +
       "a.id, a.holder, COUNT(t.id)) " +
       "FROM Account a LEFT JOIN a.transactions t " +
       "GROUP BY a.id")
List<AccountDto> findAllWithTransactionCount();

// ❌ NUNCA - Lazy loading = N+1
@Query("SELECT a FROM Account a")
List<Account> findAll();
// then: account.getTransactions() in loop = N queries
```

---

## 🔄 SDD Workflow (When You See It)

If orchestrator says: `/sdd-explore`, `/sdd-propose`, `/sdd-spec`, `/sdd-design`, `/sdd-tasks`, `/sdd-apply`, `/sdd-verify`:

1. **You are a specialist agent** - you do ONE phase well
2. **Read the orchestrator's prompt** - it tells you what artifact to read/write
3. **Save to Engram** - use `mem_save` with topic_key
4. **Return ONE message** - executive summary + artifacts

Example topic_key: `sdd/kafka-event-driven/spec`

---

## 🎓 Learning Path (Read in Order)

### New to Project?
1. `.gentle/context/project-roadmap.md` ← You are here
2. `AGENTS.md` ← Code review rules
3. `.atl/SKILL_NAVIGATION_INDEX.md` ← Find skills

### Starting a Feature?
1. Identify your layer (Domain/App/Infra/Presentation)
2. Go to `.atl/SKILL_NAVIGATION_INDEX.md`
3. Find your scenario → Read the skill
4. Copy pattern → Adapt to your context

### Debugging N+1 or Performance?
→ Read: `.atl/skill-spring-data-jpa-repositories.md`

### Writing Tests?
→ Read: `.atl/skill-spring-boot-testing-junit5-complete.md`

### Mapping DTOs?
→ Read: `.atl/skill-spring-boot-mapstruct-dtos.md`

### Implementing OAuth2/JWT?
→ Read: `.atl/skill-spring-security-oauth2.md`

### Validating Input?
→ Read: `.atl/skill-spring-boot-validation.md`

---

## 🚀 Workflow for Agentes

### Scenario: "Implement account transfer"

```
1. ORCHESTRATOR assigns: "domain-agent, implement transfer domain logic"
   → Read: .gentle/skills/java-records-dtos.md (for Money value object)
   → Read: .gentle/skills/java-optional-handling.md (for error cases)
   → Write: com.banco.co.domain.account.Account#transfer()
   
2. ORCHESTRATOR assigns: "app-agent, create TransferUseCase"
   → Read: .atl/SKILL_NAVIGATION_INDEX.md → find "Transfer funds"
   → Read: .atl/skill-spring-boot-mapstruct-dtos.md (for DTO mapping)
   → Write: TransferService, TransferDto
   
3. ORCHESTRATOR assigns: "infra-agent, publish TransferEvent to Kafka"
   → Read: .atl/skill-kafka-async-messaging.md (coming soon!)
   → Write: TransferEventPublisher
   
4. ORCHESTRATOR assigns: "presentation-agent, create POST /transfer endpoint"
   → Read: .atl/skill-spring-boot-validation.md
   → Write: TransferController with validation
```

**KEY**: Each agent knows its layer, reads the RIGHT skill, copies the pattern.

---

## 🔗 Navigation Quick Links

| Need | File |
|------|------|
| Find a skill | `.atl/SKILL_NAVIGATION_INDEX.md` |
| Code review checklist | `AGENTS.md` → Code Review Checklist |
| Architecture reference | `.gentle/context/project-roadmap.md` |
| Exception handling | `.gentle/skills/java-exception-handling.md` |
| DTOs + Records | `.gentle/skills/java-records-dtos.md` |
| Optional handling | `.gentle/skills/java-optional-handling.md` |
| Dependency injection | `.gentle/skills/java-dependency-injection.md` |
| Git commits | `.gentle/skills/conventional-commits.md` |
| Hexagonal architecture | `.gentle/skills/hexagonal-architecture.md` |
| JUnit 5 testing | `.gentle/skills/junit5-testing-patterns.md` |

---

## 🎯 Multi-Agent Rules

1. **YOU SPECIALIZE**: Domain agent writes domain. Infra agent writes infra. NO mixing.
2. **READ SKILLS FIRST**: Don't invent patterns. Skills have banco-specific examples.
3. **LAYER SEPARATION**: Never put logic outside your layer.
4. **RECORDS ALWAYS**: No getters/setters, no @Data, no mutable classes.
5. **OPTIONAL EVERYWHERE**: Use Optional, never null.
6. **SEALED EXCEPTIONS**: Type-safe error handling.
7. **NO FIELD @AUTOWIRED**: Constructor injection only.
8. **100% CONVENTIONAL**: Commits, package names, naming.

---

## 📝 Red Flags (Stop and Ask)

If you see any of these, ASK THE ORCHESTRATOR before proceeding:

- 🚩 Circular dependencies between layers
- 🚩 Domain logic leaked into Controller
- 🚩 N+1 queries in JPA
- 🚩 .get() on Optional
- 🚩 @Autowired on fields
- 🚩 @Data classes (use Records!)
- 🚩 Business logic in @Entity
- 🚩 Mutable DTOs
- 🚩 Catch generic Exception
- 🚩 No tests written

---

## 🎓 Philosophy

**This is NOT a code generator project.**

Skills are KNOWLEDGE CARDS for specialized agents:
- They teach patterns
- They prevent mistakes
- They enforce conventions
- They document banco-specific best practices

**An agent that reads skills and follows them** is better than an agent that writes code from scratch.

---

## 🔄 How to Update This File

When:
- New multi-agent pattern discovered
- New skill created
- New convention established
- New layer added to architecture

Update this file. It's the SOURCE OF TRUTH for all Claude agents.

---

**Last Updated**: $(date)  
**SDD Context**: skill-registry-completion (complete)  
**Next Change**: kafka-event-driven-refactor (in progress)
