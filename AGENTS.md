# AGENTS.md — Banco-Service Code Review Rules

## Project Context

**Project**: banco-service
**Language**: Java 21+
**Framework**: Spring Boot 3.x/4.x
**Architecture**: Hexagonal (Ports & Adapters) + DDD + Screaming Architecture (feature-first)
**Database**: MySQL
**Messaging**: Kafka (async event publishing)
**Testing**: JUnit 5 + Mockito + Testcontainers

---

## Core Rules (Non-Negotiable)

- **Records for DTOs**: All value objects MUST be `record`, never `@Data` or mutable classes
- **Constructor Injection**: Spring beans use constructor-only DI, no `@Autowired` on fields
- **Optional Handling**: NEVER call `.get()` without `isPresent()` check — always use `orElseThrow` or `orElse`
- **Abstract Exceptions**: Exception hierarchies MUST use `abstract` classes extending `RuntimeException`
- **Package Naming**: Feature-first under `com.banco.co.{feature}.*` — never global roots like `com.banco.co.services.*`

---

## Architecture Layers

| Layer | Allowed | Forbidden |
|-------|---------|-----------|
| **Domain** (`model/`, `enums/`, `exception/`) | Business logic, @Entity, enums, abstract exceptions | @Service, @RestController |
| **Application** (`service/`, `dto/`, `mapper/`) | Use cases, record DTOs, MapStruct mappers | @Entity, @RestController, JPA queries |
| **Infrastructure** (`repository/`, `config/`, `adapter/`) | JPA repos, Kafka publishers, security config | Business logic, DTOs |
| **Presentation** (`controller/`, `handler/`) | REST endpoints, HTTP mapping | Business logic, JPA queries |

---

## Spring Data JPA

- Use `@Query` with explicit `JOIN FETCH` to prevent N+1 queries
- Add `SELECT DISTINCT` when fetch-joining collections to avoid `IncorrectResultSizeDataAccessException`
- Always add `@Transactional(readOnly = true)` on query methods
- Repositories return `Optional<T>`, never `null`

---

## Spring Security

- JWT in `Authorization: Bearer <token>` header
- `SecurityFilterChain` bean only — never `WebSecurityConfigurerAdapter`
- `@PreAuthorize` on all endpoints requiring a specific role
- No secrets, passwords, or tokens in logs or code

---

## Testing

- **Domain**: 90%+ coverage, NO mocks
- **Application**: 85%+ coverage, mock ports only
- **Infrastructure**: 70%+ coverage, Testcontainers (no mocked Kafka)
- **Presentation**: 75%+ coverage, @WebMvcTest
- Naming: `test<Method>_<Condition>_<Expected>`

---

## Red Flags — Block on Any of These

🚩 `@Autowired` on fields
🚩 Mutable DTO (`@Data`, setters) — use Records
🚩 `Optional.get()` without guard
🚩 N+1 query (lazy load inside loop, missing JOIN FETCH)
🚩 Non-abstract exception base class
🚩 Missing `@Transactional(readOnly = true)` on read methods
🚩 Hardcoded secret or password in source
🚩 Stack trace exposed in HTTP response body
🚩 `@Entity` outside `{feature}/model/`
🚩 Business logic in `@RestController`
