<p align="center">
  <img
    src="https://capsule-render.vercel.app/api?type=waving&color=0:0ea5e9,100:22c55e&height=180&section=header&text=Bank-project%20%E2%80%94%20banco-service&fontSize=38&fontColor=ffffff&animation=fadeIn&desc=Java%2021%2B%20%7C%20Spring%20Boot%204%20%7C%20MySQL%20%7C%20Kafka%20%7C%20Flyway%20%7C%20JWT&descAlignY=70"
    alt="Bank-project — banco-service banner"
  />
</p>

# Bank-project — banco-service

> **v0.9.3-SNAPSHOT** · Java 21 · Spring Boot 4.0.2

Servicio backend bancario construido con **Java 21 + Spring Boot 4**, siguiendo **Hexagonal Architecture**, **DDD** y **Screaming Architecture** (feature-first). Diseñado como proyecto de portafolio de nivel profesional.

---

## Inicio rápido

### 1. Clonar y preparar variables

Linux/macOS:
```bash
cp .env.example .env
```

Windows (PowerShell):
```powershell
Copy-Item .env.example .env
```

### 2. Levantar infraestructura (MySQL + Kafka)

```bash
docker compose up -d
```

### 3. Ejecutar la aplicación

Desde la **raíz** del repo:

Linux/macOS:
```bash
./banco-service/mvnw -f banco-service/pom.xml spring-boot:run
```

Windows:
```bat
banco-service\mvnw.cmd -f banco-service/pom.xml spring-boot:run
```

### 4. Verificar

```bash
curl http://localhost:8080/actuator/health
```

```json
{ "status": "UP" }
```

---

## Stack

| Categoría | Tecnología |
|-----------|-----------|
| **Runtime** | Java 21 |
| **Framework** | Spring Boot 4.0.2 |
| **Web** | Spring Web MVC |
| **Persistencia** | Spring Data JPA + MySQL |
| **Migraciones** | Flyway (Core + MySQL) |
| **Mensajería** | Spring Kafka |
| **Seguridad** | Spring Security + JWT (`com.auth0:java-jwt`) + OAuth2 Client |
| **Encriptación** | Jasypt |
| **Email** | Spring Mail + Thymeleaf |
| **Mapeo** | MapStruct + Lombok |
| **Config local** | springboot4-dotenv (`.env` compartido) |
| **Observabilidad** | Spring Boot Actuator |
| **Testing** | JUnit 5, Mockito, Testcontainers, spring-kafka-test, GreenMail |

---

## Dominios del servicio

El servicio cubre **13 dominios**, organizados en paquetes feature-first bajo `com.banco.co.*`:

| Dominio | Responsabilidad |
|---------|----------------|
| `account` | Cuentas bancarias — creación, balance, estado, cierre |
| `auditLog` | Auditoría — registro inmutable de acciones del sistema |
| `auth` | Autenticación — login, logout, refresh tokens JWT |
| `card` | Tarjetas — ciclo de vida (activar, bloquear, robar, cerrar) y límites |
| `envelope` | Sobres de ahorro — metas, depósitos, retiros, búsqueda |
| `fraud` | Detección de fraude — Risk Profile Gate (decisión síncrona + enriquecimiento async) |
| `notification` | Notificaciones por email — Outbox Pattern, plantillas Thymeleaf |
| `outbox` | Transactional Outbox — publicación durable de eventos a Kafka |
| `permission` | Permisos — gestión y asignación |
| `role` | Roles — RBAC, configuración de matriz de permisos |
| `security` | Infraestructura de seguridad — JWT filter chain, token lifecycle, crypto |
| `transaction` | Transacciones — transferencias, pagos, programación, reversión, fraude |
| `user` | Usuarios — perfil, contraseña, administración de empleados |

---

## Arquitectura

```
com.banco.co.{feature}
│
├── model/        ← Domain: @Entity + lógica de negocio + value objects
├── enums/        ← Domain: enumeraciones del dominio
├── exception/    ← Domain: jerarquía de excepciones abstractas
│
├── service/      ← Application: casos de uso, orquestación
├── dto/          ← Application: Records (inmutables)
├── mapper/       ← Application: MapStruct mappers
│
├── repository/   ← Infrastructure: interfaces JPA
│
└── controller/   ← Presentation: REST endpoints, validaciones
```

**Convenciones no negociables**:
- DTOs como `record` (nunca clases mutables)
- Constructor injection (nunca `@Autowired` en campos)
- `Optional` sin `.get()` — siempre `orElseThrow` / `orElse`
- Excepciones base siempre `abstract`
- Sin N+1: `JOIN FETCH` o proyección DTO en queries JPA

---

## API Reference

**57 endpoints** organizados en tres niveles de acceso: `user`, `admin`, `teller`.
Especificación completa en [`docs/api/openapi.yaml`](docs/api/openapi.yaml).

### Accounts `/api/v1/accounts`

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/accounts` | Crear cuenta |
| `GET` | `/api/v1/accounts/{id}` | Obtener cuenta por ID |
| `GET` | `/api/v1/accounts/code/{code}` | Obtener cuenta por código |
| `GET` | `/api/v1/accounts/{id}/balance` | Consultar balance |
| `PUT` | `/api/v1/admin/accounts/{id}/status` | Cambiar estado (admin) |
| `DELETE` | `/api/v1/admin/accounts/{id}/close` | Cerrar cuenta (admin) |

### Cards `/api/v1/cards`

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/cards` | Emitir tarjeta |
| `GET` | `/api/v1/cards/account/{accountCode}` | Tarjetas de una cuenta |
| `GET` | `/api/v1/cards/{cardCode}` | Detalle de tarjeta |
| `PATCH` | `/api/v1/cards/{cardCode}/activate` | Activar |
| `PATCH` | `/api/v1/cards/{cardCode}/block` | Bloquear |
| `PATCH` | `/api/v1/cards/{cardCode}/report-stolen` | Reportar robada |
| `PATCH` | `/api/v1/cards/{cardCode}/report-lost` | Reportar perdida |
| `PATCH` | `/api/v1/cards/{cardCode}/close` | Cerrar |
| `PATCH` | `/api/v1/cards/{cardCode}/limits` | Actualizar límites |
| `PATCH` | `/api/v1/cards/{cardCode}/features` | Actualizar características |
| `GET` | `/api/v1/admin/cards` | Listar todas (admin) |
| `PATCH` | `/api/v1/admin/cards/{cardCode}/status` | Cambiar estado (admin) |
| `POST` | `/api/v1/admin/cards/{cardCode}/reset-pin` | Resetear PIN (admin) |

### Transactions `/api/v1/transactions`

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/transactions/transfer` | Transferencia |
| `POST` | `/api/v1/transactions/payment` | Pago |
| `POST` | `/api/v1/transactions/pay-service` | Pago de servicio |
| `POST` | `/api/v1/transactions/schedule` | Programar transacción |
| `PATCH` | `/api/v1/transactions/{id}/schedule` | Modificar programación |
| `POST` | `/api/v1/transactions/{id}/reversal` | Solicitar reversión |
| `GET` | `/api/v1/transactions/me` | Mis transacciones |
| `GET` | `/api/v1/transactions/{id}` | Detalle |
| `GET` | `/api/v1/transactions/account/{code}` | Por cuenta |
| `GET` | `/api/v1/transactions/categories` | Categorías disponibles |
| `GET` | `/api/v1/transactions/summary` | Resumen |
| `GET` | `/api/v1/admin/transactions` | Listar todas (admin) |
| `GET` | `/api/v1/admin/transactions/suspicious` | Sospechosas (admin) |
| `POST` | `/api/v1/admin/transactions/{id}/approve` | Aprobar (admin) |
| `POST` | `/api/v1/admin/transactions/{id}/reject` | Rechazar (admin) |
| `POST` | `/api/v1/admin/transactions/{id}/reverse` | Revertir (admin) |
| `POST` | `/api/v1/admin/transactions/{id}/fraud` | Marcar fraude (admin) |
| `POST` | `/api/v1/teller/transactions/cash-deposit` | Depósito en efectivo (teller) |
| `POST` | `/api/v1/teller/transactions/cash-withdrawal` | Retiro en efectivo (teller) |
| `POST` | `/api/v1/teller/transactions/check-deposit` | Depósito con cheque (teller) |

### Envelopes `/api/v1/envelopes`

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/envelopes` | Crear sobre |
| `GET` | `/api/v1/envelopes/{code}` | Detalle |
| `GET` | `/api/v1/envelopes/account/{accountCode}` | Por cuenta |
| `GET` | `/api/v1/envelopes/status/{status}` | Por estado |
| `GET` | `/api/v1/envelopes/type/{type}` | Por tipo |
| `GET` | `/api/v1/envelopes/search` | Búsqueda |
| `GET` | `/api/v1/envelopes/created-after` | Creados después de fecha |
| `POST` | `/api/v1/envelopes/deposit` | Depositar en sobre |
| `POST` | `/api/v1/envelopes/withdraw` | Retirar de sobre |

### Users `/api/v1/users`

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/public/users/register` | Registro (público) |
| `GET` | `/api/v1/users/me` | Mi perfil |
| `PATCH` | `/api/v1/users/me/password` | Cambiar contraseña |
| `GET` | `/api/v1/admin/users/{id}` | Detalle usuario (admin) |
| `PATCH` | `/api/v1/admin/users/{id}/suspend` | Suspender (admin) |
| `PATCH` | `/api/v1/admin/users/{id}/activate` | Activar (admin) |
| `PATCH` | `/api/v1/admin/users/{id}/status` | Cambiar estado (admin) |
| `GET` | `/api/v1/admin/users/employees` | Listar empleados (admin) |

---

## Patrones de diseño notables

### Transactional Outbox (Kafka)

Los eventos de negocio se persisten en `outbox_events` dentro de la misma transacción que el cambio de estado. Un scheduler (`OutboxScheduler`) los publica a Kafka y los marca como procesados. Esto garantiza **exactly-once delivery** sin two-phase commit.

```
Transacción DB  →  outbox_events (PENDING)
                       ↓ scheduler
               Kafka publish → status = PUBLISHED
```

Documentación: [`docs/OUTBOX.md`](docs/OUTBOX.md)

### Email Outbox Pattern

Sistema análogo para emails. Los emails se encolan en `email_outbox_events` y un relay los despacha vía SMTP de forma asíncrona y resiliente.

```
Kafka Consumer (TransactionCompleted)
       ↓
email_outbox_events (PENDING)
       ↓ EmailOutboxRelay
SMTP send → status = SENT
```

Documentación: [`docs/email-notification-system.md`](docs/email-notification-system.md)

### Risk Profile Gate (Antifraud)

Decisión antifraude **síncrona** sobre un perfil de riesgo materializado. Kafka enriquece el perfil asincrónicamente; el gate consulta el read model para decidir en el camino crítico de autorización.

- `CLEAR` → continúa flujo normal
- `SUSPICIOUS` → marca para revisión (`PENDING_REVIEW`)
- `BLOCKED` → bloquea y persiste traza de rechazo

Documentación: [`docs/README-antifraud-risk-profile-gate.md`](docs/README-antifraud-risk-profile-gate.md)

### Refresh Token Rotation

Los JWT de acceso son de corta duración. Los refresh tokens se persisten en `refresh_tokens` con rotación en cada uso. Tokens legacy migrados en Flyway V8.

---

## Configuración

Se usa un único `.env` compartido entre Docker Compose y Spring Boot (vía `springboot4-dotenv`).

```bash
cp .env.example .env   # Linux/macOS
Copy-Item .env.example .env   # Windows PowerShell
```

### Variables obligatorias

| Variable | Descripción |
|----------|-------------|
| `MYSQL_ROOT_PASSWORD` | Contraseña root MySQL (Docker) |
| `DB_URL` | JDBC URL de la base de datos |
| `DB_USERNAME` | Usuario de base de datos |
| `DB_PASSWORD` | Contraseña de base de datos |
| `JWT_SECRET_KEY` | Clave para firma de JWT |
| `ISSUER_GENERATOR` | Issuer del JWT |
| `JASYPT_ENCRYPTOR_PASSWORD` | Clave de encriptación de propiedades |

### Variables opcionales (con defaults)

| Variable | Default | Descripción |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Servidor Kafka |
| `SPRINGDOTENV_DIRECTORY` | `..` (directorio padre) | Ruta al `.env` desde Spring Boot |

### Prioridad de resolución

1. Variables de entorno del sistema/proceso
2. Archivo `.env`
3. Defaults en `application.yml`

> `.env` está en `.gitignore`. Nunca commitear secretos reales.

---

## Infraestructura local

```bash
docker compose up -d
```

Levanta:
- **MySQL** en `localhost:3306` (DB: `banco_db`)
- **Kafka** en `localhost:9092`

---

## Migraciones Flyway

| Versión | Archivo | Descripción |
|---------|---------|-------------|
| V1 | `V1__create_outbox_events.sql` | Tabla `outbox_events` (Kafka Outbox) |
| V2 | `V2__drop_available_balance_column.sql` | Limpieza de columna deprecada |
| V3 | `V3__add_outbox_claim_columns.sql` | Columnas de claim para procesamiento concurrente |
| V4 | `V4__create_risk_profile_tables.sql` | Tablas de perfil de riesgo (antifraud) |
| V5 | `V5__add_risk_profile_event_processing_status.sql` | Estado de procesamiento de eventos |
| V6 | `V6__create_cards_table.sql` | Tabla de tarjetas |
| V7 | `V7__create_refresh_tokens_table.sql` | Tabla de refresh tokens |
| V7_1 | `V7_1__fix_refresh_tokens_schema.sql` | Corrección de esquema |
| V8 | `V8__migrate_legacy_refresh_tokens.sql` | Migración de tokens legacy |
| V9 | `V9__drop_legacy_refresh_columns.sql` | Limpieza de columnas legacy |
| V10 | `V10__create_email_outbox_events.sql` | Tabla `email_outbox_events` (Email Outbox) |
| V11 | `V11__expand_email_outbox_event_id.sql` | Expand `event_id` a VARCHAR(100) |
| V12 | `V12__fix_permissions_column_lengths.sql` | Corrección de longitudes en permisos |

Flyway busca migraciones en `classpath:db/migration`.

---

## Error Handling Contract

Todas las respuestas de error del servicio siguen este contrato uniforme:

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Validation failed",
  "details": {
    "field": "must not be blank"
  },
  "timestamp": "2026-04-29T10:00:00"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `errorCode` | `string` | Código semántico del error |
| `message` | `string` | Descripción legible |
| `details` | `map` | Detalle por campo (validaciones) o contexto adicional |
| `timestamp` | `datetime` | Momento del error |

**Componentes clave**:
- `GlobalExceptionHandler` — maneja errores MVC
- `RestAuthenticationEntryPoint` — respuestas `401`
- `RestAccessDeniedHandler` — respuestas `403`
- `ErrorResponseFactory` — construcción centralizada del payload
- `ErrorCodeCatalog` — catálogo de códigos semánticos

---

## Seguridad

- **JWT** en header `Authorization: Bearer <token>`
- **Refresh Token Rotation** — tokens de corta duración con rotación persistida
- **RBAC** — `@PreAuthorize` con roles y scopes (estrategia híbrida)
- **Jasypt** — encriptación de propiedades sensibles en `application.yml`
- **SecurityFilterChain** bean (nunca `WebSecurityConfigurerAdapter`)
- **Datos sensibles** no loggeados (contraseñas, tokens, PANs)

ADR sobre convergencia de authorities y scopes: [`docs/adr-0001-security-authorities-scope-convergence.md`](docs/adr-0001-security-authorities-scope-convergence.md)

---

## Testing

### Cobertura mínima por capa

| Capa | Mínimo | Herramienta |
|------|--------|-------------|
| Domain | 90% | JUnit 5, sin mocks |
| Application | 85% | JUnit 5 + Mockito |
| Infrastructure | 70% | Testcontainers |
| Presentation | 75% | `@WebMvcTest` |

### Suite focalizada (error contract + seguridad)

```bash
./banco-service/mvnw -Dtest=ExceptionHierarchyGovernanceTest,GlobalExceptionHandlerWebMvcTest,RestAuthenticationEntryPointTest,RestAccessDeniedHandlerTest test -f banco-service/pom.xml
```

### Naming convention

```
Unit:         test<Method>_<Condition>_<Expected>
              testWithdraw_InsufficientFunds_ThrowsException

Integration:  test<Scenario>_<Expected>
              testCreateAccount_ValidData_ReturnsCreatedAndPublishesEvent
```

---

## Requisitos

- **Docker + Docker Compose** (MySQL + Kafka local)
- **JDK 21+**
- Maven via wrapper incluido: `banco-service/mvnw`

---

## Documentación adicional

| Documento | Descripción |
|-----------|-------------|
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml) | Especificación OpenAPI completa (57 endpoints) |
| [`docs/OUTBOX.md`](docs/OUTBOX.md) | Transactional Outbox Pattern — Kafka |
| [`docs/email-notification-system.md`](docs/email-notification-system.md) | Email Outbox Pattern — notificaciones por email |
| [`docs/README-antifraud-risk-profile-gate.md`](docs/README-antifraud-risk-profile-gate.md) | Risk Profile Gate — detección de fraude |
| [`docs/adr-0001-security-authorities-scope-convergence.md`](docs/adr-0001-security-authorities-scope-convergence.md) | ADR: convergencia de authorities y scopes |
| [`docs/diagrams/`](docs/diagrams/) | Diagramas de flujo (auth, account lifecycle, etc.) |

---

<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:22c55e,100:0ea5e9&height=100&section=footer" />
</p>
