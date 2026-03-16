# claude.md — Multi-Agent Context for Banco-Service

**Propósito**: Brújula para agentes Claude que trabajan en banco-service. Define el rol de cada agente, cuándo se activa, qué skills leer, y qué reglas jamás romper.

---

## 🏗️ Arquitectura Multi-Agente (Híbrida)

El sistema usa **7 agentes en 3 categorías**. La separación no es arbitraria: Planning y Build siguen el ciclo de vida del software, Build se especializa por capa (protege la arquitectura hexagonal), y QA+Security son cross-cutting porque aplican a TODO.

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

## 🤖 Los 7 Agentes

### CATEGORÍA 1 — PLANNING

| Agente | Se activa cuando | Entrada | Salida |
|--------|-----------------|---------|--------|
| **Planning Agent** | Empezás un feature, refactor, o decisión de diseño | Descripción del cambio | Proposal + Spec + Design + Tasks (SDD artifacts) |

**Skills que lee**:
- `.gentle/context/project-roadmap.md`
- `.gentle/skills/hexagonal-architecture.md`
- `.atl/SKILL_NAVIGATION_INDEX.md` (para entender qué existe)

**Regla clave**: Planning Agent NO escribe código. Entrega artifacts SDD. Si escribe código, está haciendo el trabajo de Build.

---

### CATEGORÍA 2 — BUILD

Cada agente de Build SOLO toca su capa. Si una tarea requiere tocar dos capas → el orquestador la divide en dos tareas para dos agentes distintos.

> **Estructura actual**: Screaming Architecture (feature-first). Los packages son `com.banco.co.{feature}.*` donde `{feature}` = `account`, `transaction`, `envelope`, `user`, `card`, `role`, `permission`, `fraud`, `auditLog`, `security`, `exception`. Dentro de cada feature existen sub-paquetes por capa (ver tabla abajo).

| Agente | Sub-paquete que toca | Responsabilidad |
|--------|----------------------|-----------------|
| **Domain Agent** | `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/` | Value objects, modelos de dominio, enums, jerarquías de excepción sealed |
| **Application Agent** | `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/` | Use cases, services, DTOs (Records), MapStruct mappers |
| **Infrastructure Agent** | `{feature}/repository/`, `security/`, `exception/` (global) | JPA entities, repositories, Kafka publishers (cuando existan), adapters, config de seguridad |
| **Presentation Agent** | `{feature}/controller/`, `{feature}/handler/` | REST controllers, exception handlers, request/response mapping |

#### Domain Agent

**Puede tocar**: `{feature}/model/`, `{feature}/enums/`, `{feature}/exception/`  
**No puede tocar**: `@Entity`, `@Service`, `@RestController`, nada de Spring  
**Skills que lee**:
- `.gentle/skills/java-records-dtos.md`
- `.gentle/skills/java-optional-handling.md`
- `.gentle/skills/java-exception-handling.md`
- `.gentle/skills/hexagonal-architecture.md`

#### Application Agent

**Puede tocar**: `{feature}/service/`, `{feature}/dto/`, `{feature}/mapper/`  
**No puede tocar**: `@Entity`, `@RestController`, lógica de negocio pura  
**Skills que lee**:
- `.atl/skill-spring-boot-mapstruct-dtos.md`
- `.gentle/skills/java-optional-handling.md`
- `.gentle/skills/java-records-dtos.md`

#### Infrastructure Agent

**Puede tocar**: `{feature}/repository/`, `security/`, `exception/` (global), `{feature}/config/`  
**No puede tocar**: Lógica de negocio, DTOs de response, controllers  
**Skills que lee**:
- `.atl/skill-spring-data-jpa-repositories.md`
- `.atl/skill-kafka-async-messaging.md` ← **PENDIENTE** (crear cuando se implemente Kafka; usar `.atl/skill-spring-data-jpa-repositories.md` mientras tanto)
- `.gentle/skills/java-dependency-injection.md`

#### Presentation Agent

**Puede tocar**: `{feature}/controller/`, `{feature}/handler/`  
**No puede tocar**: Lógica de negocio, queries JPA, publicación de eventos  
**Skills que lee**:
- `.atl/skill-spring-boot-validation.md`
- `.atl/skill-spring-security-oauth2.md`
- `.gentle/skills/java-exception-handling.md`

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

**Skills que lee**:
- `.atl/skill-spring-boot-testing-junit5-complete.md`
- `.gentle/skills/junit5-testing-patterns.md`

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

**Skills que lee**:
- `.atl/skill-spring-security-oauth2.md`
- `AGENTS.md` → sección Spring Security / OAuth2

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
2. ¿Qué agente soy?           → Infrastructure Agent
3. ¿Existe skill para esto?   → .atl/SKILL_NAVIGATION_INDEX.md
4. Leer la skill              → .atl/skill-spring-data-jpa-repositories.md
5. Copiar el patrón ✅         → adaptar solo nombres/datos
6. Escribir código            → siguiendo exactamente el patrón
```

**REGLA DE ORO**: Si la skill tiene un patrón ✅ para tu caso → copialo. No inventes. No "mejores". El patrón existe porque alguien ya resolvió ese problema.

### Encontrar la skill correcta

**Opción A — por escenario** (recomendada):
```
.atl/SKILL_NAVIGATION_INDEX.md
→ Buscar: "Map entity to DTO"
→ Resultado: skill-spring-boot-mapstruct-dtos
→ Leer: .atl/skill-spring-boot-mapstruct-dtos.md
```

**Opción B — por capa**:
```
.atl/skill-registry.md
→ Buscar: tu capa (Application, Infrastructure, etc.)
→ Encontrar el escenario
→ Leer la skill linkeada
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
│  └─ @Repository, @Entity, Kafka publishers
│     INPUT:  Domain objects desde Application
│     OUTPUT: Persisted data / published events
│     REGLA:  Implementa puertos. No tiene opinión de negocio.
│
└─ Domain (Pure Logic)
   └─ Value objects, aggregates, domain services
      INPUT:  Primitivos / value objects
      OUTPUT: Resultado de negocio o excepción sealed
      REGLA:  ZERO Spring. ZERO JPA. 100% testeable sin mocks.
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
public class AccountService {
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

### 4. Sealed exceptions

```java
// ✅ CORRECTO
public sealed class BancoException extends RuntimeException
    permits InsufficientFundsException,
            AccountNotFoundException,
            TransferFailedException {}

public final class InsufficientFundsException extends BancoException {
    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super("Required: " + required + ", available: " + available);
    }
}

// ❌ NUNCA
public class BancoException extends Exception {} // no sellada = jerarquía descontrolada
```

### 5. Package naming

```
✅ CORRECTO
com.banco.co.domain.account.Account
com.banco.co.application.service.AccountService
com.banco.co.infrastructure.persistence.JpaAccountRepository
com.banco.co.presentation.controller.AccountController

❌ NUNCA
com.myapp.Account
org.example.AccountService
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

[Domain Agent]  ← Task 1
  → Lee: java-records-dtos.md, java-exception-handling.md
  → Escribe: Account#transfer(), InsufficientFundsException
  → Capa: com.banco.co.domain.account.*

[Application Agent]  ← Task 2 (parallel con Domain)
  → Lee: skill-spring-boot-mapstruct-dtos.md
  → Escribe: TransferService, TransferDto, TransferResponseDto
  → Capa: com.banco.co.application.*

[Infrastructure Agent]  ← Task 3 (después de Domain)
  → Lee: skill-spring-data-jpa-repositories.md  (skill-kafka-async-messaging.md aún no existe — crear en sprint Kafka)
  → Escribe: TransferEventPublisher, OutboxEntry
  → Capa: com.banco.co.infrastructure.*

[Presentation Agent]  ← Task 4 (después de Application)
  → Lee: skill-spring-boot-validation.md
  → Escribe: TransferController, GlobalExceptionHandler
  → Capa: com.banco.co.presentation.*

[Test Agent]  ← Task 5 (después de todos los Build)
  → Lee: skill-spring-boot-testing-junit5-complete.md
  → Escribe: tests para TODAS las capas anteriores
  → Coverage: Domain 90%, Application 85%, Infra 70%, Presentation 75%

[Security Agent]  ← Task 6 (parallel con Test Agent)
  → Lee: skill-spring-security-oauth2.md
  → Revisa: POST /transfer requiere auth, datos sensibles no loggeados
  → Agrega: @PreAuthorize, JWT validation, CORS config
```

**KEY**: Planning entrega el plan → Build construye capa por capa → QA+Security validan todo.

---

## 🔗 Navigation Quick Links

| Necesito... | Archivo |
|-------------|---------|
| Encontrar una skill | `.atl/SKILL_NAVIGATION_INDEX.md` |
| Ver todas las skills por capa | `.atl/skill-registry.md` |
| Checklist pre-merge | `AGENTS.md` → Code Review Checklist |
| Arquitectura del proyecto | `.gentle/context/project-roadmap.md` |
| Records / DTOs | `.gentle/skills/java-records-dtos.md` |
| Optional handling | `.gentle/skills/java-optional-handling.md` |
| Constructor injection | `.gentle/skills/java-dependency-injection.md` |
| Sealed exceptions | `.gentle/skills/java-exception-handling.md` |
| Arquitectura hexagonal | `.gentle/skills/hexagonal-architecture.md` |
| Git commits | `.gentle/skills/conventional-commits.md` |
| Testing JUnit 5 | `.gentle/skills/junit5-testing-patterns.md` |
| JPA + N+1 prevention | `.atl/skill-spring-data-jpa-repositories.md` |
| MapStruct mappers | `.atl/skill-spring-boot-mapstruct-dtos.md` |
| OAuth2 / JWT | `.atl/skill-spring-security-oauth2.md` |
| Validación de inputs | `.atl/skill-spring-boot-validation.md` |
| Tests completos | `.atl/skill-spring-boot-testing-junit5-complete.md` |

---

## 📋 Reglas Multi-Agente

1. **ESPECIALIZATE**: Cada agente solo toca su capa/responsabilidad. Sin excepciones.
2. **LEÉ SKILLS PRIMERO**: Antes de escribir una línea. Sin excepciones.
3. **PLANNING antes de BUILD**: Sin spec ni tasks, Build no arranca.
4. **TEST y SECURITY siempre**: No se entregan features sin Test Agent y Security Agent.
5. **RECORDS siempre**: Ningún DTO es una clase mutable.
6. **OPTIONAL sin .get()**: Siempre `orElseThrow` o `orElse`.
7. **CONSTRUCTOR INJECTION**: Nunca `@Autowired` en campos.
8. **SEALED EXCEPTIONS**: Toda jerarquía de excepciones es sellada.
9. **PACKAGE NAMING**: Todo bajo `com.banco.co.*`.
10. **CONVENTIONAL COMMITS**: `feat(domain):`, `feat(infrastructure):`, `test(application):`, etc.

---

## 🚩 Red Flags — Parar y consultar al orquestador

**Arquitectura**
- 🚩 Lógica de negocio en un `@RestController`
- 🚩 `@Entity` o `@Repository` en el domain layer
- 🚩 Dependencia circular entre capas
- 🚩 Business logic en un `@Entity`

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

**Last Updated**: 2026-03-16
**Arquitectura**: Híbrida — Planning / Build (x4) / QA+Security
**SDD en curso**: `kafka-event-driven-refactor`
