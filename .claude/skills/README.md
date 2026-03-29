# Skills Index — banco-service

All skills follow the pattern: `{skill-name}/SKILL.md` (patterns + anti-patterns) and `{skill-name}/README.md` (description).

## Available Skills

| Skill | When to Use |
|-------|-------------|
| [`spring-boot-patterns`](./spring-boot-patterns/SKILL.md) | Writing any @Service, @Configuration, DTO (Record), MapStruct mapper, Kafka config, or SecurityFilterChain |
| [`logging-patterns`](./logging-patterns/SKILL.md) | Adding log statements to any class — levels, parameterized logging, MDC, never logging sensitive data |
| [`jpa-patterns`](./jpa-patterns/SKILL.md) | Writing @Entity classes, repository interfaces, @Transactional methods, or implementing the Outbox pattern |
| [`java-migration`](./java-migration/SKILL.md) | Creating Flyway SQL migration files — naming, MySQL conventions, expand-contract for rename/drop |
| [`java-code-review`](./java-code-review/SKILL.md) | Reviewing PRs or writing tests — instant-reject checklist and layer-specific test patterns |
| [`design-patterns`](./design-patterns/SKILL.md) | Designing a new feature — Screaming Architecture, Strategy, Factory, Repository port/adapter, Outbox |
| [`clean-code`](./clean-code/SKILL.md) | Naming classes/methods/constants, structuring methods, Optional handling, exception hierarchy |
| [`api-contract-review`](./api-contract-review/SKILL.md) | Designing or reviewing REST endpoints — HTTP verbs, @Valid, @PreAuthorize, GlobalExceptionHandler |
| [`audit-logging-patterns`](./audit-logging-patterns/SKILL.md) | Calling IAuditLogService in any @Service — logSuccess, logFailure, logCritical, logAnonymous, AuditLogDetail context |
| [`release-please`](./release-please/SKILL.md) | Creating a release, bumping version, updating CHANGELOG, or any versioning/release automation question |

## Quick Reference

```
Need to...                                  → Read this skill
─────────────────────────────────────────────────────────────
Write a @Service with dependencies          → spring-boot-patterns
Write a DTO (record)                        → spring-boot-patterns
Write a MapStruct mapper                    → spring-boot-patterns
Configure Spring Security                   → spring-boot-patterns
Add a log statement                         → logging-patterns
Log a Kafka event                           → logging-patterns
Write a JPA @Entity                         → jpa-patterns
Write a @Query / prevent N+1                → jpa-patterns
Use @Transactional correctly                → jpa-patterns
Implement Outbox pattern                    → jpa-patterns
Write a Flyway migration SQL file           → java-migration
Review a PR before merge                    → java-code-review
Write tests for domain layer                → java-code-review
Write tests for service layer               → java-code-review
Write @WebMvcTest for controller            → java-code-review
Write Testcontainers integration test       → java-code-review
Structure a new feature package             → design-patterns
Apply Strategy or Factory pattern           → design-patterns
Name a class, method, or constant           → clean-code
Handle Optional                             → clean-code
Create an exception hierarchy               → clean-code
Design a REST endpoint                      → api-contract-review
Add @PreAuthorize to controller             → api-contract-review
Implement GlobalExceptionHandler            → api-contract-review
Log an audit event in a @Service           → audit-logging-patterns
Choose logSuccess vs logFailure vs logCritical → audit-logging-patterns
Log a security violation or fraud event    → audit-logging-patterns
Log an unauthenticated (anonymous) action  → audit-logging-patterns
Build AuditLogDetail context for a CREATE/UPDATE → audit-logging-patterns
Create a new release / bump version         → release-please
Update CHANGELOG.md                         → release-please
Understand what commit triggers major/minor → release-please
```
