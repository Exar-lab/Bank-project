# claude.md — Multi-Agent Context for Banco-Service

**Propósito**: Brújula para agentes Claude que trabajan en banco-service. Define el rol de cada agente, cuándo se activa, qué skills leer, y qué reglas jamás romper.

---

## 🏗️ Arquitectura Multi-Agente (Híbrida)

El sistema usa **8 agentes en 3 categorías**. La separación no es arbitraria: Planning y Build siguen el ciclo de vida del software, Build se especializa por capa (protege la arquitectura hexagonal), y QA+Security son cross-cutting porque aplican a TODO.

```
┌─────────────────────────────────────────────────────────────┐
│  CATEGORÍA 1: PLANNING                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Planning Agent  →  SDD workflow, specs, diseño      │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────┘
                          ↓ (entrega tasks + specs)
┌─────────────────────────────────────────────────────────────┐
│  CATEGORÍA 2: BUILD (especializados por capa)               │
│  ┌────────────┐ ┌─────────────┐ ┌────────────────────────┐  │
│  │   Domain   │ │ Application │ │    Infrastructure      │  │
│  │   Agent    │ │   Agent     │ │       Agent            │  │
│  └────────────┘ └─────────────┘ └────────────────────────┘  │
│                 ┌─────────────┐                             │
│                 │Presentation │                             │
│                 │   Agent     │                             │
│                 └─────────────┘                             │
└─────────────────────────┬───────────────────────────────────┘
                          ↓ (entrega código)
┌─────────────────────────────────────────────────────────────┐
│  CATEGORÍA 3: QA + SECURITY (cross-cutting)                 │
│  ┌──────────────────┐   ┌─────────────────────────────┐    │
│  │   Test Agent     │   │      Security Agent         │    │
│  │  (todas las      │   │   (todas las capas,         │    │
│  │   capas)         │   │    siempre)                 │    │
│  └──────────────────┘   └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🤖 Los 8 Agentes

### CATEGORÍA 1 — PLANNING

| Agente | Se activa cuando | Entrada | Salida |
|--------|-----------------|---------|--------|
| **Planning Agent** | Empezás un feature, refactor, o decisión de diseño | Descripción del cambio | Proposal + Spec + Design + Tasks (SDD artifacts) |

**Agent file**: `.claude/agents/java-architect.md`

**Skills que lee**:
- `.claude/skills/design-patterns/SKILL.md`
- `.claude/skills/clean-code/SKILL.md`
- `.claude/skills/README.md` (índice de todas las skills)

**Regla clave**: Planning Agent NO escribe código. Entrega artifacts SDD. Si escribe código, está haciendo el trabajo de Build.

---

### CATEGORÍA 2 — BUILD

Cada agente de Build SOLO toca su capa. Si una tarea requiere tocar dos capas → el orquestador la divide en dos tareas para dos agentes distintos.

> **Estructura actual**: Screaming Architecture (feature-first). Los packages siguen la convención `com.banco.co.{feature}.*`, donde típicamente `{feature}` incluye `account`, `transaction`, `envelope`, `user`, `card`, `role`, `permission`, `fraud`, `auditLog`, `security`, `exception`, y además existen otros paquetes top-level (por ejemplo `com.banco.co.utils`). Dentro de cada feature existen sub-paquetes por capa (ver tabla abajo).

| Agente | Carpeta/Path que toca | Responsabilidad |
|--------|-----------------------|-----------------|
| **Domain Agent** | `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/` | Value objects, modelos de dominio, enums, jerarquías de excepción abstract |
| **Application Agent** | `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/` | Use cases, services, DTOs (Records), MapStruct mappers |
| **Infrastructure Agent** | `{feature}/repository/`, `security/`, `exception/` (global) | JPA entities, repositories, Kafka publishers (cuando existan), adapters, config de seguridad |
| **Presentation Agent** | `{feature}/controller/`, `{feature}/handler/` | REST controllers, exception handlers, request/response mapping |

#### Domain Agent

**Agent file**: `.claude/agents/java-architect.md`
**Puede tocar**: `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/`
**No puede tocar**: `@Service`, `@RestController` — no lógica de Application/Infrastructure/Presentation
> **Nota**: En el estado actual, las clases de `model/` llevan `@Entity` + `@EntityListeners`. El Domain Agent puede tocarlas — son el modelo del dominio.
**Skills que lee**:
- `.claude/skills/clean-code/SKILL.md`
- `.claude/skills/design-patterns/SKILL.md`

#### Application Agent

**Agent file**: `.claude/agents/spring-boot-engineer.md`
**Puede tocar**: `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/`
**No puede tocar**: `@Entity`, `@RestController`, lógica de negocio pura
**Skills que lee**:
- `.claude/skills/spring-boot-patterns/SKILL.md`
- `.claude/skills/clean-code/SKILL.md`

#### Infrastructure Agent

**Agent file**: `.claude/agents/spring-boot-engineer.md`
**Puede tocar**: `{feature}/repository/`, `security/`, `exception/` (global), `com.banco.co.security.config`, `com.banco.co.role.configuration` *(y futuros paquetes `{feature}.config` si se definen explícitamente)*
**No puede tocar**: Lógica de negocio, DTOs de response, controllers
**Skills que lee**:
- `.claude/skills/jpa-patterns/SKILL.md`
- `.claude/skills/spring-boot-patterns/SKILL.md`

#### Presentation Agent

**Agent file**: `.claude/agents/spring-boot-engineer.md`
**Puede tocar**: `{feature}/controller/` (cuando exista), `{feature}/handler/` (cuando exista)
**No puede tocar**: Lógica de negocio, queries JPA, publicación de eventos
**Skills que lee**:
- `.claude/skills/api-contract-review/SKILL.md`
- `.claude/skills/spring-boot-patterns/SKILL.md`

---

### CATEGORÍA 3 — QA + SECURITY

Estos dos agentes son **cross-cutting**: no tienen capa asignada. Actúan sobre lo que produce Build, en cualquier capa.

| Agente | Se activa cuando | Qué revisa/escribe |
|--------|-----------------|-------------------|
| **Test Agent** | Después de cualquier Build agent | Tests unitarios + integración para la capa que se acaba de construir |
| **Security Agent** | Después de cualquier Build agent que toque auth, datos sensibles, o endpoints | Security review, RBAC, JWT, validaciones de seguridad |

#### Test Agent

**Responsabilidad**: Escribir y verificar tests para TODAS las capas  
**Cobertura mínima por capa**:

| Capa | Mínimo | Herramienta |
|------|--------|-------------|
| Domain | 90% | JUnit 5, sin mocks |
| Application | 85% | JUnit 5 + Mockito (mock ports) |
| Infrastructure | 70% | Testcontainers |
| Presentation | 75% | @WebMvcTest |

**Agent file**: `.claude/agents/test-automator.md`

**Skills que lee**:
- `.claude/skills/java-code-review/SKILL.md`

**Naming convention**:
```
Unit:        test<Method>_<Condition>_<Expected>
             testWithdraw_InsufficientFunds_ThrowsException

Integration: test<Scenario>_<Expected>
             testCreateAccount_ValidData_ReturnsCreatedAndPublishesEvent
```

#### Security Agent

**Responsabilidad**: Revisar y reforzar seguridad en cualquier capa donde aparezca  
**Se activa ante**: Nuevo endpoint, cambio en auth, acceso a datos de usuario, publicación de eventos con datos sensibles  

**Agent file**: `.claude/agents/security-engineer.md`

**Skills que lee**:
- `.claude/skills/spring-boot-patterns/SKILL.md`
- `.claude/skills/api-contract-review/SKILL.md`
- `.claude/skills/logging-patterns/SKILL.md`

**Checklist que aplica en cada revisión**:
- [ ] JWT validado en header `Authorization: Bearer <token>`
- [ ] `@PreAuthorize` en endpoints que requieren rol específico
- [ ] Datos sensibles NO loggeados (contraseñas, tokens, PAN de tarjetas)
- [ ] Inputs sanitizados antes de persistir
- [ ] No secrets hardcodeados (API keys, passwords)
- [ ] `SecurityFilterChain` bean (nunca `WebSecurityConfigurerAdapter`)
- [ ] CORS configurado explícitamente (no `*` en producción)
- [ ] Excepciones de seguridad no exponen stack traces al cliente

---

## 📖 Cómo Leer Skills (Protocol)

### Antes de escribir código — siempre

```
1. ¿Qué necesito hacer?       → "crear un JPA repository"
2. ¿Qué agente soy?           → Infrastructure Agent (spring-boot-engineer)
3. ¿Existe skill para esto?   → .claude/skills/README.md
4. Leer la skill              → .claude/skills/jpa-patterns/SKILL.md
5. Copiar el patrón ✅         → adaptar solo nombres/datos
6. Escribir código            → siguiendo exactamente el patrón
```

**REGLA DE ORO**: Si la skill tiene un patrón ✅ para tu caso → copialo. No inventes. No "mejores". El patrón existe porque alguien ya resolvió ese problema.

### Encontrar la skill correcta

**Opción A — por escenario** (recomendada):
```
.claude/skills/README.md
→ Buscar: "Write a JPA @Entity" o "N+1 prevention"
→ Resultado: jpa-patterns
→ Leer: .claude/skills/jpa-patterns/SKILL.md
```

**Opción B — por agente**:
```
Revisar el agente file (.claude/agents/{agent}.md)
→ Sección "Mandatory Skill Reading"
→ Leer la skill indicada
```

### Estructura de cada skill

```markdown
## When to Use
- Cuándo aplica esta skill

## Critical Patterns
### ✅ Correct Pattern    ← COPIÁ ESTO
### ❌ Common Mistake     ← NUNCA HAGAS ESTO

## Examples for Banco-Service
- Ejemplos reales del proyecto

## Best Practices
- Convenciones específicas de banco-service
```

---

## 🏛️ Capas de la Arquitectura

```
┌─ Presentation (REST)
│  └─ @RestController, exception handlers
│     INPUT:  HTTP requests
│     OUTPUT: JSON + HTTP status codes
│     REGLA:  Nunca lógica de negocio aquí
│
├─ Application (Orchestration)
│  └─ @Service, use cases, MapStruct mappers
│     INPUT:  DTOs (records) desde Presentation
│     OUTPUT: DTOs (records) hacia Presentation
│     REGLA:  Orquesta, no decide. La decisión es del Domain.
│
├─ Infrastructure (Adapters)
│  └─ @Repository (interfaces JPA), Kafka publishers, security config
│     INPUT:  Domain objects desde Application
│     OUTPUT: Persisted data / published events
│     REGLA:  Implementa puertos. No tiene opinión de negocio.
│
└─ Domain (model/ + enums/ + exception/)
   └─ @Entity + lógica de negocio + enums + jerarquías de excepción
      INPUT:  Primitivos / value objects
      OUTPUT: Resultado de negocio o excepción abstract
      NOTA:   Estado actual: @Entity y lógica conviven en model/.
```

**Define tu capa PRIMERO → luego lee la skill DE ESA CAPA.**

---

## ✅ Convenciones (No Negociables)

### 1. DTOs son siempre Records

```java
// ✅ CORRECTO
public record CreateAccountDto(
    @NotBlank String accountHolder,
    @Positive BigDecimal initialBalance
) {}

// ❌ NUNCA
@Data
public class CreateAccountDto {
    private String accountHolder; // mutable = problema
}
```

### 2. Constructor injection únicamente

```java
// ✅ CORRECTO
@Service
public class AccountService {
    private final IAccountRepository repo;

    public AccountService(IAccountRepository repo) {
        this.repo = repo;
    }
}

// ❌ NUNCA
@Service
public class BadAccountService {
    @Autowired
    private IAccountRepository repo; // no testeable, acoplado al container
}
```

### 3. Optional sin .get()

```java
// ✅ CORRECTO
return repo.findById(id)
    .orElseThrow(() -> new AccountNotFoundException(id));

return repo.findById(id)
    .map(Account::getBalance)
    .orElse(BigDecimal.ZERO);

// ❌ NUNCA
return repo.findById(id).get(); // NoSuchElementException esperando el momento justo
```

### 4. Abstract exceptions

```java
// ✅ CORRECTO
public abstract class BankingException extends RuntimeException {
    protected BankingException(String message) {
        super(message);
    }
}

public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super("Required: " + required + ", available: " + available);
    }
}

// ❌ NUNCA
public class BankingException extends Exception {} // no abstract = jerarquía descontrolada
```

### 5. Package naming (feature-first / Screaming Architecture)

```
✅ CORRECTO — feature-first
com.banco.co.account.model.Account
com.banco.co.account.service.AccountService
com.banco.co.account.repository.AccountEntity
com.banco.co.account.controller.AccountController

❌ NUNCA — layer-first (rompe Screaming Architecture)
com.banco.co.services.AccountService
com.banco.co.repositories.AccountRepository
com.banco.co.controllers.AccountController
```

### 6. JPA sin N+1

```java
// ✅ CORRECTO — join explícito + proyección DTO
@Query("SELECT new com.banco.co.application.dto.AccountSummaryDto(" +
       "a.id, a.holder, COUNT(t.id)) " +
       "FROM Account a LEFT JOIN a.transactions t " +
       "GROUP BY a.id, a.holder")
@Transactional(readOnly = true)
List<AccountSummaryDto> findAllWithTransactionCount();

// ❌ NUNCA — lazy loading dentro de un loop = N queries
List<Account> accounts = repo.findAll();
accounts.forEach(a -> a.getTransactions()); // BOOM: N+1
```

---

## 🔄 SDD Workflow

Cuando el orquestador dice `/sdd-explore`, `/sdd-propose`, `/sdd-spec`, `/sdd-design`, `/sdd-tasks`, `/sdd-apply`, `/sdd-verify`:

1. **Sos un agente especialista** — hacés UNA fase bien
2. **Leé el prompt del orquestador** — te dice qué artifact leer/escribir
3. **Guardá en Engram** — `mem_save` con `topic_key`
4. **Devolvés UN mensaje** — executive summary + artifacts

**Formato de topic_key**: `sdd/{change-name}/{phase}`
Ejemplo: `sdd/kafka-event-driven-refactor/spec`

**Dependency chain**:
```
proposal → spec ──→ tasks → apply → verify → archive
            ↑
          design
```

---

## 🚀 Workflow Completo: "Implementar account transfer"

```
[Planning Agent]
  → /sdd-new account-transfer
  → Entrega: spec + design + tasks
  → Artifact: sdd/account-transfer/tasks

[Domain Agent / java-architect]  ← Task 1
  → Lee: .claude/skills/clean-code/SKILL.md, .claude/skills/design-patterns/SKILL.md
  → Escribe: Account#transfer(), InsufficientFundsException
  → Capa: com.banco.co.account.model.*, com.banco.co.account.exception.*

[Application Agent / spring-boot-engineer]  ← Task 2 (parallel con Domain)
  → Lee: .claude/skills/spring-boot-patterns/SKILL.md
  → Escribe: TransferService, TransferDto, TransferResponseDto
  → Capa: com.banco.co.account.service.*, com.banco.co.account.dto.*

[Infrastructure Agent / spring-boot-engineer]  ← Task 3 (después de Domain)
  → Lee: .claude/skills/jpa-patterns/SKILL.md, .claude/skills/spring-boot-patterns/SKILL.md
  → Escribe: TransferEventPublisher, OutboxEventEntity
  → Capa: com.banco.co.account.repository.*

[Presentation Agent / spring-boot-engineer]  ← Task 4 (después de Application)
  → Lee: .claude/skills/api-contract-review/SKILL.md
  → Escribe: TransferController, GlobalExceptionHandler
  → Capa: com.banco.co.account.controller.*

[Test Agent / test-automator]  ← Task 5 (después de todos los Build)
  → Lee: .claude/skills/java-code-review/SKILL.md
  → Escribe: tests para TODAS las capas anteriores
  → Coverage: Domain 90%, Application 85%, Infra 70%, Presentation 75%

[Security Agent / security-engineer]  ← Task 6 (parallel con Test Agent)
  → Lee: .claude/skills/spring-boot-patterns/SKILL.md, .claude/skills/api-contract-review/SKILL.md
  → Revisa: POST /transfer requiere auth, datos sensibles no loggeados
  → Agrega: @PreAuthorize, JWT validation, CORS config
```

**KEY**: Planning entrega el plan → Build construye capa por capa → QA+Security validan todo.

---

## 🔗 Navigation Quick Links

| Necesito... | Archivo |
|-------------|---------|
| Índice de todas las skills | `.claude/skills/README.md` |
| Agentes disponibles | `.claude/agents/` |
| Records / DTOs / Constructor injection | `.claude/skills/spring-boot-patterns/SKILL.md` |
| MapStruct mappers | `.claude/skills/spring-boot-patterns/SKILL.md` |
| Security / JWT / SecurityFilterChain | `.claude/skills/spring-boot-patterns/SKILL.md` |
| Optional handling / Abstract exceptions | `.claude/skills/clean-code/SKILL.md` |
| Naming / Method length / Clean code | `.claude/skills/clean-code/SKILL.md` |
| Arquitectura hexagonal / Screaming / Patterns | `.claude/skills/design-patterns/SKILL.md` |
| JPA + N+1 prevention / @Transactional | `.claude/skills/jpa-patterns/SKILL.md` |
| Outbox pattern | `.claude/skills/jpa-patterns/SKILL.md` |
| Flyway migrations (MySQL) | `.claude/skills/java-migration/SKILL.md` |
| Release automation / CHANGELOG / versioning | `.claude/skills/release-please/SKILL.md` |
| Logging / MDC / Sensitive data | `.claude/skills/logging-patterns/SKILL.md` |
| Kafka event logging | `.claude/skills/logging-patterns/SKILL.md` |
| REST API / @Valid / @PreAuthorize | `.claude/skills/api-contract-review/SKILL.md` |
| GlobalExceptionHandler | `.claude/skills/api-contract-review/SKILL.md` |
| Code review checklist | `.claude/skills/java-code-review/SKILL.md` |
| Testing JUnit 5 / Mockito / Testcontainers | `.claude/skills/java-code-review/SKILL.md` |
| Git commits | `feat(domain):`, `feat(infrastructure):`, `test(application):` (inline convention) |

---

## 📋 Reglas Multi-Agente

1. **ESPECIALIZATE**: Cada agente solo toca su capa/responsabilidad. Sin excepciones.
2. **LEÉ SKILLS PRIMERO**: Antes de escribir una línea. Sin excepciones.
3. **PLANNING antes de BUILD**: Sin spec ni tasks, Build no arranca.
4. **TEST y SECURITY siempre**: No se entregan features sin Test Agent y Security Agent.
5. **RECORDS siempre**: Ningún DTO es una clase mutable.
6. **OPTIONAL sin .get()**: Siempre `orElseThrow` o `orElse`.
7. **CONSTRUCTOR INJECTION**: Nunca `@Autowired` en campos.
8. **ABSTRACT EXCEPTIONS**: Toda jerarquía de excepciones usa clases abstract.
9. **PACKAGE NAMING**: Todo bajo `com.banco.co.*`.
10. **CONVENTIONAL COMMITS**: `feat(domain):`, `feat(infrastructure):`, `test(application):`, etc.
11. **CONVENTIONAL COMMITS para releases**: Cada commit que llegue a `master` debe seguir conventional commits — release-please lee el historial para calcular el próximo bump. Un `feat:` sin contexto rompe el CHANGELOG tanto como uno bien formado lo mejora.

---

## 🚩 Red Flags — Parar y consultar al orquestador

**Arquitectura**
- 🚩 Lógica de negocio en un `@RestController`
- 🚩 Business logic en un `@Service` que debería estar en el dominio
- 🚩 Dependencia circular entre capas
- 🚩 `@Entity` fuera de `{feature}/model/` (en service, controller, dto, etc.)

**Código**
- 🚩 `.get()` en un `Optional`
- 🚩 `@Autowired` en un campo
- 🚩 `@Data` en un DTO (usar Record)
- 🚩 `catch (Exception e)` genérico
- 🚩 N+1 query detectada (lazy loading en loop)

**Testing**
- 🚩 Cero tests escritos para la feature
- 🚩 Test agent mockeando Kafka (usar Testcontainers)
- 🚩 Coverage por debajo del mínimo por capa

**Seguridad**
- 🚩 Endpoint sin `@PreAuthorize` que requiere auth
- 🚩 Password, token, o secreto loggeado
- 🚩 Secret hardcodeado en código fuente
- 🚩 Stack trace expuesto en response HTTP
- 🚩 CORS configurado con `*` en producción

---

## 🎓 Filosofía

**Este no es un proyecto de generación de código.**

Los agentes son especialistas que conocen su dominio en profundidad. Un agente que:
- lee su skill antes de escribir
- se mantiene en su capa
- pasa el código a Test Agent y Security Agent

...vale diez veces más que un agente que escribe todo de corrido sin leer nada.

Las skills son el conocimiento acumulado del equipo. No las ignores.

---

## 🔄 Cómo actualizar este archivo

Actualizá `claude.md` cuando:
- Se agrega un nuevo agente
- Se crea una nueva skill
- Se establece una nueva convención
- Cambia la arquitectura del proyecto

Este archivo es el SOURCE OF TRUTH. Si algo cambia en el sistema y no está acá, los agentes van a seguir el comportamiento viejo.

---

**Last Updated**: 2026-03-21
**Arquitectura**: Híbrida — Planning / Build (x4) / QA+Security
**Agents**: `.claude/agents/` — java-architect, spring-boot-engineer, security-engineer, test-automator, code-reviewer, devops-engineer, docker-expert, kubernetes-specialist
**Skills**: `.claude/skills/` — subdirectory structure, each with SKILL.md + README.md
**SDD en curso**: `kafka-event-driven-refactor`
