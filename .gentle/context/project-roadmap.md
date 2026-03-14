# Banco-Service Project Context

## Project Overview

**Microservicio**: `banco-service`  
**Objetivo**: Implementar sistema bancario con arquitectura hexagonal  
**Lenguaje**: Java 21+  
**Framework**: Spring Boot 3.x  
**Arquitectura**: Hexagonal (Ports & Adapters)  

## What We're Building

Un sistema bancario completo con:
- ✅ Gestión de cuentas (CRUD)
- ✅ Transferencias entre cuentas
- ✅ Historial de transacciones
- ✅ Validaciones de negocio
- ✅ Manejo de errores global
- ✅ Event sourcing (publicación de eventos)

## Technology Stack

```
Backend:
- Java 21 (Records, Virtual Threads, Pattern Matching)
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL
- Kafka (evento publishing)
- JUnit 5 + Mockito
- Testcontainers

Architecture:
- Hexagonal Architecture (Clean Architecture)
- Domain-Driven Design
- SOLID Principles
```

## Project Structure

```
banco-service/
├── src/main/java/com/banco/
│   ├── domain/              # Core business logic
│   ├── application/         # Use cases
│   ├── infrastructure/      # Adapters (DB, messaging)
│   └── presentation/        # REST controllers
├── src/test/java/com/banco/ # Tests
├── .gentle/
│   ├── skills/             # Reusable skill cards
│   └── context/            # Project documentation
└── pom.xml
```

## Git Workflow

1. **Branch**: `feat/feature-name` or `fix/bug-name`
2. **Commit**: Follow Conventional Commits format
3. **Tests**: Must pass before merge
4. **PR**: Title follows format `feat(scope): description`

Example:
```bash
git checkout -b feat/account-withdrawal
git add .
gga run           # Code review via GGA
git commit -m "feat(domain): add Account withdrawal method"
git push origin feat/account-withdrawal
```

## Development Guidelines

### Code Quality

- ✅ Use Records for DTOs
- ✅ Use Optional (never .get())
- ✅ Constructor injection only
- ✅ Sealed exception hierarchies
- ✅ Domain layer: NO Spring, NO JPA annotations

### Testing

- **Domain tests**: 90%+ coverage (no mocks)
- **Application tests**: 85%+ coverage (mock ports)
- **Infrastructure tests**: 70%+ coverage (Testcontainers)

### Architecture

- **Domain**: Pure business logic
- **Application**: Orchestration, DTOs, mappers
- **Infrastructure**: Repository implementations, adapters
- **Presentation**: REST endpoints, exception handlers

## Active Skills

When developing, reference:

1. **java-records-dtos.md** - Creating DTOs and Value Objects
2. **java-optional-handling.md** - Safe null handling
3. **java-dependency-injection.md** - Spring bean configuration
4. **java-exception-handling.md** - Exception hierarchies
5. **hexagonal-architecture.md** - Layer structure
6. **junit5-testing-patterns.md** - Writing tests
7. **conventional-commits.md** - Git commit format

## Commands Cheat Sheet

```bash
# Create feature branch
git checkout -b feat/feature-name

# Review code with GGA
gga run

# Stage and commit
git add .
git commit -m "feat(scope): description"

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=AccountTest

# Build project
mvn clean install

# Start application
mvn spring-boot:run
```

## Next Steps

1. ✅ Create project structure (.gentle/skills, .gentle/context)
2. ⏳ Implement domain models (Account, Transaction, Transfer)
3. ⏳ Create repository interfaces and implementations
4. ⏳ Build REST controllers
5. ⏳ Setup event publishing (Kafka)
6. ⏳ Add comprehensive tests

## Key Principles

| Principle | Rule |
|-----------|------|
| **Immutability** | Domain models use Records |
| **Testability** | Domain layer independent of Spring |
| **Separation** | Each layer has clear responsibility |
| **Type Safety** | Sealed exceptions, non-null by default |
| **Clarity** | Code should read like documentation |

## Common Mistakes to Avoid

❌ Field injection (@Autowired on fields)  
❌ Getter/setter methods (use Records)  
❌ Null pointer exceptions (use Optional)  
❌ Generic exceptions (use domain-specific)  
❌ Business logic in controllers  
❌ Using .get() on Optional  
❌ Setter injection or autowired setters  

## Questions? Reference Skills

Each skill file contains:
- Problem description
- Correct pattern with examples
- Common mistakes
- Best practices specific to our context
