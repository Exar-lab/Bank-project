# AGENTS.md — Banco-Service Code Review Rules

## Project Context

**Project**: banco-service  
**Language**: Java 21+  
**Framework**: Spring Boot 3.x/4.x  
**Architecture**: Hexagonal (Ports & Adapters) + DDD + Screaming Architecture (feature-first)  
**Database**: MySQL/PostgreSQL  
**Messaging**: Kafka (async event publishing)  
**Testing**: JUnit 5 + Mockito + Testcontainers  

---

## Core Rules (Non-Negotiable)

### Java

- **Records for DTOs**: All value objects MUST be `record`, never `@Data` or mutable classes
- **Constructor Injection**: Spring beans use constructor-only DI, no `@Autowired` on fields
- **Optional Handling**: Use `Optional` for nullability; NEVER call `.get()` without `isPresent()` check
- **Sealed Exceptions**: Exception hierarchies MUST be sealed (e.g., `public sealed class BancoException { }`)
- **Package Naming**: All code under `com.banco.co.*`
- **Annotations**: Use only javax/jakarta (Spring 6+), never com.sun or internal APIs

### Package Organization (Screaming Architecture)

- **Feature-First Root**: Organize by feature under `com.banco.co.{feature}.*`, not by global layer roots
- **Current Main Features**: `account`, `transaction`, `envelope`, `user`, `card`, `role`, `permission`, `fraud`, `auditLog`, `security`, `exception` (plus supporting packages such as `utils`)
- **Layering Is Conceptual + Local to Feature**: Domain/Application/Infrastructure/Presentation are preserved as responsibilities, but materialized inside each feature via subpackages (for example `model`, `service`, `repository`, `controller`, `dto`, `mapper`, `exception`, `handler`)
- **Avoid Fake Paths**: Do not document or assume root packages like `com.banco.co.domain.*`, `com.banco.co.application.*`, `com.banco.co.infrastructure.*`, `com.banco.co.presentation.*` unless they are explicitly created in code

### Architecture Layers

| Layer | Rules | Examples |
|-------|-------|----------|
| **Domain** | No Spring, no JPA, pure logic | `com.banco.co.account.model.Account`, `com.banco.co.card.exception.card.CardException` |
| **Application** | Orchestrates domain, DTOs, mappers | `com.banco.co.account.service.AccountService`, `com.banco.co.user.dto.*`, `com.banco.co.account.mapper.*` |
| **Infrastructure** | Repository impls, external adapters, persistence/security config | `com.banco.co.account.repository.*`, `com.banco.co.security.config.*` |
| **Presentation** | REST endpoints, exception handlers, request/response mapping | `com.banco.co.{feature}.controller.*`, `com.banco.co.{feature}.handler.*` |

Layer boundaries remain mandatory, but package layout is feature-first.

### Multi-Agent Operating Model

This repository follows a **7-agent model in 3 categories**. Activation and ownership are explicit to protect hexagonal boundaries and feature-first structure.

| Category | Agent | Activate When | Main Tasks / Output | Boundaries |
|----------|-------|---------------|---------------------|------------|
| **Planning** | **Planning Agent** | A new feature, refactor, or architectural decision starts | Produces SDD artifacts: proposal, spec, design, tasks | Does not write production code |
| **Build** | **Domain Agent** | Domain rules, value objects, enums, or domain exceptions change | Updates domain model and sealed exception hierarchies | Touches only `com.banco.co.{feature}.model`, `com.banco.co.{feature}.enums`, `com.banco.co.{feature}.exception`; no Spring/JPA/controllers |
| **Build** | **Application Agent** | Use-case orchestration, DTO mapping, or service flow changes | Implements application services, record DTOs, mappers | Touches only `com.banco.co.{feature}.service`, `com.banco.co.{feature}.dto`, `com.banco.co.{feature}.mapper`; no entities/repositories/controllers |
| **Build** | **Infrastructure Agent** | Persistence, external adapters, messaging, or security config changes | Implements repositories/adapters and infrastructure configuration | Touches `com.banco.co.{feature}.repository` plus explicit infrastructure packages like `com.banco.co.security.config`; no domain business decisions |
| **Build** | **Presentation Agent** | REST contract, validation entrypoints, or error handling at API layer changes | Implements controllers and HTTP exception handlers | Touches only `com.banco.co.{feature}.controller`, `com.banco.co.{feature}.handler`; no domain logic or JPA query logic |
| **QA+Security** | **Test Agent** | After any Build change | Adds/updates unit/integration tests and validates coverage targets by layer | Cross-cutting; tests all layers but does not redefine architecture ownership |
| **QA+Security** | **Security Agent** | Any auth, endpoint, sensitive-data, or access-control change | Performs security review and hardening (JWT, RBAC, validation, secret handling) | Cross-cutting; enforces security controls without changing functional scope |

Operational notes:
- Keep ownership **feature-first** under `com.banco.co.{feature}.*`; do not introduce or document fake global roots (`com.banco.co.domain.*`, `com.banco.co.application.*`, etc.) unless they are explicitly created.
- If a task spans multiple layers, split work by agent instead of allowing one agent to cross boundaries.

### Spring Data JPA

- Use `@Query` with explicit joins to prevent N+1 queries
- Use DTO projections (interfaces or `new` constructor syntax) for read queries
- Always add `@Transactional(readOnly = true)` for queries
- Repositories return `Optional<T>`, never throw `EntityNotFoundException`

### Spring Security / OAuth2

- JWT tokens in Authorization header: `Bearer <token>`
- `SecurityFilterChain` bean for custom filters (not `WebSecurityConfigurerAdapter`)
- RBAC via `@PreAuthorize("hasRole('ADMIN')")` on endpoints
- OAuth2ResourceServerConfigurer for JWT validation

### Validation

- Use `@Validated` on `@RestController` classes
- Custom `@Constraint` annotations for domain-level validation
- Validation groups for multi-stage validation
- Return HTTP 400 (Bad Request) with error details in response body

### Testing

- **Domain layer**: 90%+ coverage, NO mocks
- **Application layer**: 85%+ coverage, mock ports only
- **Infrastructure layer**: 70%+ coverage with Testcontainers
- Unit test naming: `test<Method><Condition><Expected>` (e.g., `testWithdraw_InsufficientFunds_ThrowsException`)
- Integration test naming: `test<Scenario>_<Expected>`

### Git & Commits

- **Conventional Commits**: `type(scope): subject`
  - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
  - Scope: `domain`, `application`, `infrastructure`, `presentation`, or affected feature (`account`, `transaction`, `security`, etc.)
  - Subject: lowercase, no period, imperative mood
- **PR Title**: `feat(scope): description` (same format as commit)
- **No AI Attribution**: Never add "Co-Authored-By: AI/Claude"

---

## Code Review Checklist

### Before Merge

- [ ] All tests pass locally
- [ ] Code follows package naming `com.banco.co.*`
- [ ] Package structure follows feature-first convention `com.banco.co.{feature}.*`
- [ ] DTOs are Records (not classes)
- [ ] No `@Autowired` on fields (constructor injection only)
- [ ] No `.get()` on Optional without `isPresent()`
- [ ] No hardcoded secrets (API keys, passwords)
- [ ] JPA queries use projections to avoid N+1
- [ ] Exception handling uses sealed classes
- [ ] Commit message is conventional format
- [ ] Code coverage meets minimum (see testing section)

### Red Flags

🚩 `@Autowired` on fields → Forces field injection, untestable  
🚩 Mutable DTOs (`@Data`, setters) → Use Records  
🚩 `Optional.get()` without guard → NPE in production  
🚩 N+1 queries → Always use joins or projections  
🚩 Checked exceptions → Use sealed exception hierarchies  
🚩 No @Transactional(readOnly=true) on queries → Unnecessary write overhead  

---

## GGA (Gentleman Guardian Angel) Integration

This project uses **GGA v2.7.3** for AI-assisted code review. When running:

```bash
gga run
```

GGA will enforce:
1. All rules above
2. SOLID principles
3. Clean code formatting
4. Security best practices (no secrets in code)
5. Performance (N+1 detection, etc.)

If GGA blocks commit, fix issues and retry. Never use `--no-verify`.

---

## Skill References

When developing new features, reference:

| Need | Skill |
|------|-------|
| Query with JPA | `skill-spring-data-jpa-repositories.md` |
| Map DTO from entity | `skill-spring-boot-mapstruct-dtos.md` |
| Implement OAuth2 / JWT | `skill-spring-security-oauth2.md` |
| Add validation | `skill-spring-boot-validation.md` |
| Write tests | `skill-spring-boot-testing-junit5-complete.md` |
| **Find which skill to use** | `SKILL_NAVIGATION_INDEX.md` ⭐ |

---

## SDD Methodology

For substantial features (>2 hours work):

```bash
/sdd-new feature-name
```

This triggers:
1. **Exploration** → Understand problem
2. **Proposal** → Scope, effort, approach
3. **Specification** → Requirements + scenarios
4. **Design** → Architecture decisions
5. **Tasks** → Implementation checklist
6. **Implementation** → Code with SDD tracking
7. **Verification** → All specs met + tests pass
8. **Archive** → Persistent record for Release Please

See `.atl/SKILL_NAVIGATION_INDEX.md` for full workflow.

---

## Questions?

- Architecture decisions → Check `.gentle/context/project-roadmap.md`
- How to X → Check `.atl/SKILL_NAVIGATION_INDEX.md`
- SDD methodology → See `/sdd-explore` or `/sdd-new`
- Code review rules → This file (AGENTS.md)
