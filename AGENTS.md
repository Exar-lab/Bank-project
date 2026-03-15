# AGENTS.md — Banco-Service Code Review Rules

## Project Context

**Project**: banco-service  
**Language**: Java 21+  
**Framework**: Spring Boot 3.x/4.x  
**Architecture**: Hexagonal (Ports & Adapters) + Domain-Driven Design  
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

### Architecture Layers

| Layer | Rules | Examples |
|-------|-------|----------|
| **Domain** | No Spring, no JPA, pure logic | `Account`, `Money`, `Transaction` value objects |
| **Application** | Orchestrates domain, DTOs, mappers | Use cases, service interfaces, MapStruct mappers |
| **Infrastructure** | Repository impls, external adapters, JPA entities | `@Repository`, `@Entity`, Kafka publishers |
| **Presentation** | REST endpoints, exception handlers, request/response mapping | `@RestController`, `@ExceptionHandler` |

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
  - Scope: domain, application, infrastructure, presentation, skills
  - Subject: lowercase, no period, imperative mood
- **PR Title**: `feat(scope): description` (same format as commit)
- **No AI Attribution**: Never add "Co-Authored-By: AI/Claude"

---

## Code Review Checklist

### Before Merge

- [ ] All tests pass locally
- [ ] Code follows package naming `com.banco.co.*`
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
