# Skill Registry — banco-service

**Project**: banco-service (Java 21 + Spring Boot 4.0.2)  
**Last Updated**: 2026-03-13  
**Persistence**: Engram

---

## How to Use This Registry

When implementing a task or fixing a bug, search this table for your scenario. Load the listed skill(s) BEFORE writing code. Each skill contains patterns, conventions, and examples specific to this project.

If multiple skills apply, load all of them and reconcile any overlaps.

---

## Skill Directory

### Global Skills (User-Level)

These skills are available in `~/.config/opencode/skills/`:

| Skill Name | Purpose | When to Use |
|-----------|---------|------------|
| `sdd-init` | Initialize SDD context in any project | First time setup, re-syncing project state |
| `sdd-propose` | Create change proposals | Planning a new feature or substantial refactor |
| `sdd-spec` | Write specifications with scenarios | Defining requirements before implementation |
| `sdd-design` | Technical design with architecture decisions | Complex features needing architecture review |
| `sdd-tasks` | Break down changes into implementation tasks | Creating task checklists for features |
| `sdd-apply` | Implement tasks from SDD specs | Writing actual code following specs |
| `sdd-verify` | Validate implementation matches specs | Before marking feature complete |
| `sdd-archive` | Sync deltas to main specs, archive completed change | After verify passes |
| `sdd-explore` | Investigate ideas before committing to a change | Thinking through unclear features |
| `go-testing` | Go testing patterns, Bubbletea TUI testing | Writing Go tests (not applicable to banco-service) |
| `skill-creator` | Create new AI agent skills | Adding new agent skills to the project |

---

## Project Context Documents

These files live in `.claude/context/` and provide project-specific guidance:

| Document | Purpose | Key Reference |
|----------|---------|--------|
| `project-roadmap.md` | Project overview, tech stack, active skills | Architecture principles, development guidelines, common mistakes |

---

## Scenario-Based Skill Selection

### I need to create a new feature (e.g., add Card management)

1. **Load**: `sdd-explore` → investigate the idea
2. **Load**: `sdd-propose` → draft change proposal (scope, affected modules, rollback plan)
3. **Load**: `sdd-spec` → write Given/When/Then scenarios
4. **Load**: `sdd-design` → document architecture decisions (if complex)
5. **Load**: `sdd-tasks` → break into implementation tasks
6. **Load**: `sdd-apply` → implement code following specs

| Skill | Why |
|-------|-----|
| `sdd-propose` | Clarify scope and identify breaking changes |
| `sdd-spec` | Define exact behavior before coding |
| `sdd-design` | Complex features (cross-layer effects) need architecture review |
| `sdd-tasks` | Prevent scope creep, track progress |
| `sdd-apply` | Follow project patterns: Records for DTOs, Optional handling, hexagonal layers |

---

### I need to fix a bug (e.g., JWT token expiration not working)

1. **Load**: `sdd-explore` → isolate the bug, understand root cause
2. **Load**: `sdd-apply` → implement fix (if small) or create a task breakdown (if large)
3. **Load**: `sdd-verify` → test fix against spec scenarios

| Skill | Why |
|-------|-----|
| `sdd-explore` | Understand what's broken and why |
| `sdd-apply` | Apply project patterns when fixing |
| `sdd-verify` | Ensure fix doesn't break existing behavior |

---

### I need to refactor a layer (e.g., improve transaction service)

1. **Load**: `sdd-propose` → justify the refactor (performance, maintainability, technical debt)
2. **Load**: `sdd-design` → document architecture changes
3. **Load**: `sdd-tasks` → break refactor into safe, testable increments
4. **Load**: `sdd-apply` → implement following hexagonal architecture
5. **Load**: `sdd-verify` → ensure behavior unchanged, tests pass

| Skill | Why |
|-------|-----|
| `sdd-propose` | Justify refactor; refactors without clear ROI waste time |
| `sdd-design` | Document layers (domain, application, infrastructure) |
| `sdd-apply` | Follow project conventions (no business logic in controllers, etc.) |
| `sdd-verify` | Regression testing is critical for refactors |

---

### I need to add tests to improve coverage

1. **Load**: `sdd-spec` → understand what behavior to test
2. **Load**: `sdd-apply` → write tests following project patterns (Spring Boot test starters, JUnit5)
3. **Load**: `sdd-verify` → validate test coverage meets targets (Domain 90%+, App 85%+, Infra 70%+)

| Skill | Why |
|-------|-----|
| `sdd-spec` | Tests should verify spec scenarios |
| `sdd-apply` | Use correct test framework and mocking strategy |
| `sdd-verify` | Measure coverage and confirm test quality |

---

## Technical Skills for banco-service

### Quick Reference: Find Skills by Scenario

| **Developer Scenario** | **Load This Skill** | **Package/Layer** |
|---|---|---|
| I need to query accounts with filters | `spring-data-jpa-repositories` | com.banco.co.account.repository |
| I need to paginate large result sets | `spring-data-jpa-repositories` | com.banco.co.account.repository |
| I need to prevent N+1 queries | `spring-data-jpa-repositories` | com.banco.co.account.repository |
| I need to map Account → AccountResponseDto | `spring-boot-mapstruct-dtos` | com.banco.co.account.mapper |
| I need to handle nested objects in mapping | `spring-boot-mapstruct-dtos` | com.banco.co.account.mapper |
| I need custom field transformation during mapping | `spring-boot-mapstruct-dtos` | com.banco.co.account.mapper |
| I need to secure an endpoint with roles | `spring-security-oauth2` | com.banco.co.security.config |
| I need to validate JWT tokens | `spring-security-oauth2` | com.banco.co.security.config |
| I need to implement OAuth2 client credentials flow | `spring-security-oauth2` | com.banco.co.security.config |
| I need to validate request DTOs | `spring-boot-validation` | com.banco.co.account.dto |
| I need custom validation rules | `spring-boot-validation` | com.banco.co.account.dto |
| I need method-level validation | `spring-boot-validation` | com.banco.co.account.service |
| I need to test a JPA repository | `spring-boot-testing-junit5-complete` | com.banco.co.account.repository |
| I need to test a secured endpoint | `spring-boot-testing-junit5-complete` | com.banco.co.account.controller |
| I need to mock external services | `spring-boot-testing-junit5-complete` | com.banco.co.account.service |
| I need to test with a real database | `spring-boot-testing-junit5-complete` | com.banco.co.account.integration |

---

### Skills by Architecture Layer

#### **Domain Layer** (com.banco.co.model.*)
**Focus**: Entity design, validation rules, domain exceptions

- **spring-boot-validation**: Validate domain entities with custom constraints
- **spring-boot-mapstruct-dtos**: Map domain entities to presentation DTOs

#### **Application Layer** (com.banco.co.*.service, com.banco.co.*.application.*)
**Focus**: Business logic, orchestration, use cases

- **spring-data-jpa-repositories**: Query repositories for business operations
- **spring-security-oauth2**: Extract principal and check permissions in services
- **spring-boot-testing-junit5-complete**: Test business logic with 85%+ coverage
- **spring-boot-validation**: Validate business inputs with custom rules

#### **Infrastructure Layer** (com.banco.co.*.repository, com.banco.co.*.config, com.banco.co.*.infrastructure.*)
**Focus**: Persistence, security config, adapters

- **spring-data-jpa-repositories**: Implement custom repository queries
- **spring-boot-mapstruct-dtos**: Map JPA results to application DTOs
- **spring-security-oauth2**: Configure SecurityFilterChain, JWT validation
- **spring-boot-testing-junit5-complete**: Test repositories with @DataJpaTest or TestContainers

#### **Presentation Layer** (com.banco.co.*.controller)
**Focus**: HTTP endpoints, input validation, response formatting

- **spring-boot-validation**: Validate @RequestBody with @Valid
- **spring-boot-mapstruct-dtos**: Map response DTOs to HTTP JSON
- **spring-security-oauth2**: Extract JWT principal and check @PreAuthorize
- **spring-boot-testing-junit5-complete**: Test endpoints with @WebMvcTest

---

### Technical Skill Reference

#### 1. **spring-data-jpa-repositories**
**Purpose**: Repository patterns, custom queries, pagination, N+1 prevention  
**File**: [skill-spring-data-jpa-repositories.md](./skill-spring-data-jpa-repositories.md)  
**Key Topics**:
- Custom @Query with JPQL/native SQL
- @EntityGraph to prevent N+1
- Pagination with Pageable
- DTO projection for memory efficiency
- Method naming conventions

**Example Scenarios**:
- Scenario 1: Query accounts with filters
- Scenario 2: Paginate transactions
- Scenario 3: Prevent N+1 with JOIN FETCH

---

#### 2. **spring-boot-mapstruct-dtos**
**Purpose**: DTO mapping, nested objects, custom transformations  
**File**: [skill-spring-boot-mapstruct-dtos.md](./skill-spring-boot-mapstruct-dtos.md)  
**Key Topics**:
- @Mapper with componentModel
- @Mapping field transforms
- Nested collections
- Enum mapping
- Partial updates with nullValuePropertyMappingStrategy

**Example Scenarios**:
- Scenario 1: Simple entity → DTO mapping
- Scenario 2: Nested collections (Account + Transactions)
- Scenario 3: Enum to string transformation

---

#### 3. **spring-security-oauth2**
**Purpose**: OAuth2 flows, JWT validation, RBAC, security config  
**File**: [skill-spring-security-oauth2.md](./skill-spring-security-oauth2.md)  
**Key Topics**:
- SecurityFilterChain configuration
- Custom JWT validator filters
- @PreAuthorize with role checks
- Principal extraction from SecurityContextHolder
- CORS configuration

**Example Scenarios**:
- Scenario 1: Configure SecurityFilterChain
- Scenario 2: Validate JWT tokens
- Scenario 3: Role-based access control with @PreAuthorize

---

#### 4. **spring-boot-validation**
**Purpose**: Request validation, custom constraints, validation groups  
**File**: [skill-spring-boot-validation.md](./skill-spring-boot-validation.md)  
**Key Topics**:
- Standard constraints (@NotNull, @NotBlank, @Size, @Pattern)
- @Valid on @RequestBody
- Custom @Constraint annotations
- Validation groups for different scenarios
- Error handling with @ExceptionHandler

**Example Scenarios**:
- Scenario 1: DTO with standard validation
- Scenario 2: Custom @Constraint validator
- Scenario 3: Validation groups (create vs. update)

---

#### 5. **spring-security-jwt**
**Purpose**: JWT authentication with auth0 java-jwt:4.5.0, SecurityFilterChain, RBAC  
**File**: [.claude/skills/spring-security-jwt.md](../.claude/skills/spring-security-jwt.md)  
**Key Topics**:
- Custom JWT filter with auth0 java-jwt (not Spring OAuth2 Resource Server)
- SecurityFilterChain configuration (Spring Boot 4.x)
- @PreAuthorize with role-based access control
- Principal extraction from SecurityContextHolder
- CORS and stateless session configuration

**Example Scenarios**:
- Scenario 1: Configure JWT filter with auth0 java-jwt
- Scenario 2: Secure endpoints with @PreAuthorize
- Scenario 3: Extract user from JWT token in service layer

---

#### 6. **spring-boot-testing-junit5-complete**
**Purpose**: Unit/integration tests, @DataJpaTest, @WebMvcTest, MockMvc  
**File**: [skill-spring-boot-testing-junit5-complete.md](./skill-spring-boot-testing-junit5-complete.md)  
**Key Topics**:
- @DataJpaTest for repository testing (fast, in-memory)
- @WebMvcTest for REST endpoint testing
- @SpringBootTest with TestContainers (real MySQL)
- MockMvc for HTTP simulation
- Mocking strategies (Mockito @Mock, @InjectMocks)
- Security testing with @WithMockUser

**Coverage Targets**:
- Domain Layer: 90%+
- Application Layer: 85%+
- Infrastructure Layer: 70%+

**Example Scenarios**:
- Scenario 1: Repository test with @DataJpaTest
- Scenario 2: Service test with mocked repository
- Scenario 3: Controller test with @WebMvcTest
- Scenario 4: Integration test with TestContainers

---

## Project-Specific Patterns

### Code Patterns (from project-roadmap.md)

- **DTOs**: Use Records (not classes with getters/setters)
- **Null Handling**: Always use `Optional`, never call `.get()` without `isPresent()` check
- **Dependency Injection**: Constructor injection only; NO field injection
- **Exception Handling**: Create abstract exception hierarchies; domain-specific exceptions
- **Domain Models**: No Spring annotations, no JPA in domain layer
- **REST Controllers**: Thin layer; business logic belongs in Application or Domain

### Architecture Layers (Hexagonal)

```
com.banco.co.{domain}
├── domain/               ← Pure business logic, no Spring, no JPA
├── application/          ← Use cases, DTOs, mappers, orchestration
├── infrastructure/       ← Repositories, adapters, persistence
└── presentation/         ← REST controllers, exception handlers
```

### Testing Coverage Targets

- **Domain Layer**: 90%+ coverage (pure Java, no mocks needed)
- **Application Layer**: 85%+ coverage (mock ports/repositories)
- **Infrastructure Layer**: 70%+ coverage (use Testcontainers or H2 in-memory)

---

## Environment & Configuration

**Application Properties** (`src/main/resources/application.yml`):
- Database: MySQL (via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars)
- JWT: `JWT_SECRET_KEY`, `ISSUER_GENERATOR` env vars
- Encryption: `JASYPT_ENCRYPTOR_PASSWORD` env var
- Access token: 15 minutes
- Refresh token: 30 days

**Java Version**: 21 with preview features enabled (`--enable-preview`)

**Build Tool**: Maven (pom.xml)

---

## Quick Command Reference

```bash
# Run tests
mvn test

# Test single class
mvn test -Dtest=AccountServiceTest

# Build project
mvn clean install

# Start application
mvn spring-boot:run

# Code review via GGA
gga run

# Create feature branch & commit (Conventional Commits)
git checkout -b feat/feature-name
git add .
git commit -m "feat(scope): description"
```

---

## Troubleshooting

### Question: Should I use inheritance for entities?

**Answer**: No. Use composition and abstract exception hierarchies. Domain models are Records (immutable value objects).

### Question: How do I handle database transactions?

**Answer**: Spring `@Transactional` lives in Application or Infrastructure layer, NOT Domain. Domain classes should not know about transactions.

### Question: My test is failing because of a mock. Why?

**Answer**: Check the architecture layer. Domain tests should have NO mocks (they're pure Java). Application tests mock ports (repositories, external services). Infrastructure tests use Testcontainers.

### Question: Can I use .get() on Optional?

**Answer**: No. Use `.orElseThrow()`, `.orElse(default)`, or `.ifPresentOrElse()`. Never call `.get()` without checking `.isPresent()` first — this defeats the purpose of Optional.

---

## Next Steps for New Developers

1. Read `.claude/context/project-roadmap.md` — understand architecture and conventions
2. Load `sdd-explore` → pick a small task to understand patterns
3. Load `sdd-propose` + `sdd-spec` → plan your first feature
4. Load `sdd-apply` → write code following project patterns
5. Load `sdd-verify` → test and validate against spec

---

## Support

- **SDD Documentation**: Run `mem_search(query: "sdd-init/banco-service", project: "banco-service")` to retrieve project context
- **Architecture Questions**: Read `.claude/context/project-roadmap.md` § "Architecture" and "Common Mistakes to Avoid"
- **Testing Patterns**: Check `.claude/context/project-roadmap.md` § "Testing" and load `sdd-apply` skill
