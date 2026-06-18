# Contributing to Banco Service

Thank you for helping improve Banco Service. This guide explains the fastest safe path to contribute while preserving the project's architecture, security posture, and review quality.

## Quick Path

1. Fork or branch from the latest `master`.
2. Set up the project using the [README](README.md) quick path.
3. Keep the change focused on one feature, fix, refactor, or documentation update.
4. Run the relevant tests before opening a pull request.
5. Open a PR with a clear summary, verification evidence, and linked issue when available.

## Before You Start

- Check existing issues and pull requests to avoid duplicate work.
- For substantial features or refactors expected to take more than two hours, use the SDD workflow before implementation:

  ```text
  /sdd-new feature-name
  ```

- Do not include secrets, credentials, access tokens, production data, or personal data in code, logs, tests, commits, issues, or PRs.
- If the change affects authentication, authorization, persistence, messaging, money movement, or transactional behavior, explain the risk and rollback path in the PR.

## Local Setup

Install the prerequisites:

- JDK 24 or newer
- Docker and Docker Compose
- Git

Create local configuration:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Start local infrastructure:

```bash
docker compose up -d mysql kafka
```

Run the application:

```bash
./banco-service/mvnw -f banco-service/pom.xml spring-boot:run
```

Windows:

```bat
banco-service\mvnw.cmd -f banco-service/pom.xml spring-boot:run
```

Check health:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Architecture Rules

Banco Service uses Java 24, Spring Boot 4, Hexagonal Architecture, DDD, and feature-first packages under `com.banco.co.{feature}.*`.

| Layer | Allowed | Not allowed |
|-------|---------|-------------|
| Domain: `domain/model`, `domain/port`, `enums`, `exception` | Business rules, ports, enums, abstract exceptions | Spring stereotypes, JPA entities, Spring Data imports |
| Application: `service`, `dto`, `mapper` | Use cases, record DTOs, MapStruct mappers | REST controllers, JPA queries, persistence concerns |
| Infrastructure: `adapter/out`, `repository`, `config` | JPA entities, repositories, adapters, Kafka publishers, security config | Business rules |
| Presentation: `adapter/in/rest`, `controller`, `handler` | HTTP endpoints and HTTP mapping | Business rules, JPA queries |

### Non-negotiable code rules

- DTOs and value-style API payloads must be Java `record`s, not mutable classes.
- Use constructor injection for Spring beans. Do not use field `@Autowired`.
- Do not call `Optional.get()` without a guard. Prefer `orElseThrow`, `orElse`, or explicit branching.
- Base exception types must be `abstract` classes extending `RuntimeException`.
- Keep packages feature-first. Do not create global roots such as `com.banco.co.services.*`.
- Keep domain code free of Spring, JPA, and infrastructure annotations.
- Keep business logic out of controllers and persistence adapters.

## Persistence and Transactions

- Repositories must return `Optional<T>` instead of `null` for nullable lookups.
- Use explicit `JOIN FETCH` queries when needed to prevent N+1 behavior.
- Use `SELECT DISTINCT` when fetch-joining collections.
- Add `@Transactional(readOnly = true)` to read/query methods.
- Keep schema changes in Flyway migrations.
- Do not rely on Hibernate auto-DDL for application schema changes; the project validates the schema at startup.

## Security Requirements

- JWTs are passed with the `Authorization: Bearer <token>` header.
- Use `SecurityFilterChain`; do not introduce `WebSecurityConfigurerAdapter`.
- Add `@PreAuthorize` to endpoints that require specific roles or authorities.
- Never log passwords, tokens, secrets, full authorization headers, or decrypted sensitive values.
- Return safe error responses. Do not expose stack traces or internal exception details in HTTP responses.

## Testing Expectations

Run the smallest useful test set while developing, then run broader verification before opening a PR.

```bash
./banco-service/mvnw -f banco-service/pom.xml test
./banco-service/mvnw -f banco-service/pom.xml verify
```

Windows:

```bat
banco-service\mvnw.cmd -f banco-service/pom.xml test
banco-service\mvnw.cmd -f banco-service/pom.xml verify
```

Testing rules:

- Domain tests should avoid mocks and focus on business behavior.
- Application tests may mock ports only.
- Infrastructure tests should use Testcontainers for real integration behavior where practical.
- Presentation tests should use MVC slice tests for HTTP behavior and security mapping.
- Test names should follow `test<Method>_<Condition>_<Expected>`.
- Add or update tests with every behavior change.

## Documentation Expectations

Update documentation when behavior, setup, APIs, operations, or architecture changes.

Useful checks:

```bash
git diff --check -- '*.md'
python -m unittest docs/verification/test_api_docs_consistency.py -v
```

The API documentation consistency check may report known OpenAPI/Postman drift. If your change touches API contracts, explain whether the drift is affected.

## Commit Guidelines

Use Conventional Commits:

```text
type(scope): subject
```

Examples:

```text
feat(account): add account status transition
fix(security): reject expired refresh tokens
test(transaction): cover fraud gate rejection
docs(readme): clarify local kafka setup
```

Rules:

- Use lowercase type and scope.
- Write the subject in imperative mood.
- Do not end the subject with a period.
- Keep commits focused and reviewable.
- Do not add AI attribution or `Co-Authored-By` lines unless they identify a real human collaborator.

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.

Recommended scopes: feature names such as `account`, `transaction`, `user`, `card`, `envelope`, `security`, or layer names such as `domain`, `infrastructure`, `presentation`.

## Pull Request Checklist

Before requesting review, confirm:

- [ ] The PR has one clear purpose.
- [ ] The description explains what changed and why.
- [ ] Tests were added or updated for behavior changes.
- [ ] Relevant verification commands were run and their results are listed.
- [ ] Documentation was updated when needed.
- [ ] No secrets, credentials, tokens, or sensitive data were added.
- [ ] Architecture boundaries are respected.
- [ ] Security-sensitive changes explain risks and rollback.
- [ ] Large changes are split or justified for review.

Suggested PR description:

```markdown
## Summary
-

## Verification
- [ ] `./banco-service/mvnw -f banco-service/pom.xml test`
- [ ] `./banco-service/mvnw -f banco-service/pom.xml verify`

## Risk and rollback
- Risk:
- Rollback:
```

## Issue Guidelines

When reporting a bug, include:

- What happened
- What you expected
- Steps to reproduce
- Environment details
- Logs or screenshots, with secrets removed

When proposing a feature, include:

- User or business problem
- Desired outcome
- Scope boundaries and non-goals
- Security, data, or operational implications

## Review Standards

Reviewers should block changes that introduce:

- Field injection with `@Autowired`
- Mutable DTOs where records are expected
- Unsafe `Optional.get()` usage
- N+1 query risk
- Missing read-only transactions on query methods
- Hardcoded secrets or credential leaks
- Stack traces in HTTP responses
- JPA entities outside infrastructure persistence packages
- Business logic in REST controllers
- Domain code depending on Spring or JPA

Small, well-tested, architecture-aligned PRs get reviewed faster. Keep the review surface humane.
