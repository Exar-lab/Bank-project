# Transactional Outbox — banco-service

Documentación técnica del patrón Transactional Outbox implementado en banco-service.
Refleja el estado actual del código al **2026-03-26**.

---

## Tabla de contenidos

1. [Por qué existe el Outbox](#1-por-qué-existe-el-outbox)
2. [Arquitectura general](#2-arquitectura-general)
3. [Componentes](#3-componentes)
   - [OutboxEvent (entidad)](#31-outboxevent)
   - [OutboxStatus (enum)](#32-outboxstatus)
   - [KafkaTopic (enum)](#33-kafkatopic)
   - [IOutboxEventPort (puerto)](#34-ioutboxeventport)
   - [IOutboxEventRepository (repositorio)](#35-ioutboxeventrepository)
   - [OutboxScheduler (publicador)](#36-outboxscheduler)
   - [KafkaEventPublisher (adaptador Kafka)](#37-kafkaeventpublisher)
4. [Esquema de base de datos](#4-esquema-de-base-de-datos)
5. [Productores — quién escribe al outbox](#5-productores)
6. [Consumidores — quién lee de Kafka](#6-consumidores)
7. [Flujo completo paso a paso](#7-flujo-completo-paso-a-paso)
8. [Garantías y limitaciones actuales](#8-garantías-y-limitaciones-actuales)
9. [Pasos a futuro](#9-pasos-a-futuro)

---

## 1. Por qué existe el Outbox

El problema clásico de la integración con Kafka es la doble escritura:

```
❌ Patrón ingenuo (NO usado aquí):
  1. guardar en DB  ─┐
  2. publicar Kafka  ─┘  ← si esto falla, el evento se pierde para siempre
```

El **Transactional Outbox** resuelve esto escribiendo el evento en la **misma transacción** que el dato de negocio. La publicación a Kafka ocurre después, de forma asíncrona:

```
✅ Outbox (lo que hace banco-service):
  1. guardar en DB + guardar OutboxEvent  ─── misma @Transactional
  2. OutboxScheduler (cada 5 seg) lee filas PENDING → publica a Kafka
  3. Si Kafka falla → fila queda en FAILED → se reintenta automáticamente
```

**Garantía obtenida**: at-least-once delivery. Si el proceso muere entre el paso 1 y el 2, el scheduler lo retomará.

---

## 2. Arquitectura general

```
┌──────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT APP                              │
│                                                                      │
│  ┌─────────────────┐   @Transactional   ┌──────────────────────┐    │
│  │  AccountService  │ ─────────────────▶ │  outbox_events (DB)  │    │
│  │  TransactionSvc  │   misma tx que     │  status=PENDING      │    │
│  │  EnvelopeService │   el negocio       └──────────┬───────────┘    │
│  │  UserService     │                              │                 │
│  └─────────────────┘                              │ cada 5 segundos  │
│                                                   ▼                 │
│                                      ┌────────────────────────┐     │
│                                      │    OutboxScheduler     │     │
│                                      │  findRetryable()       │     │
│                                      │  claimForProcessing()  │     │
│                                      │  findClaimed()         │     │
│                                      └──────────┬─────────────┘     │
│                                                 │                   │
│                                                 ▼                   │
│                                      ┌────────────────────────┐     │
│                                      │  KafkaEventPublisher   │     │
│                                      │  .send().get()  ←sync  │     │
│                                      │  PUBLISHED / FAILED    │     │
│                                      └──────────┬─────────────┘     │
└─────────────────────────────────────────────────┼────────────────────┘
                                                  │ Kafka
                          ┌───────────────────────┼────────────────────┐
                          ▼                        ▼                    ▼
              banco.transaction.events  banco.envelope.events  banco.account.events
                          │                        │                   (sin consumer)
                          ▼                        ▼
              ┌──────────────────────┐  ┌────────────────────────┐
              │ FraudDetectionConsumer│  │  NotificationConsumer  │
              │ filtra: Transaction  │  │  filtra: EnvelopeGoal  │
              │ Completed            │  │  Reached               │
              │ → IFraudDetection    │  │  → log.info (stub)     │
              │   Service.analyze()  │  └────────────────────────┘
              └──────────────────────┘
```

---

## 3. Componentes

### 3.1 OutboxEvent

**Archivo**: `com.banco.co.outbox.model.OutboxEvent`

Entidad JPA que representa un evento pendiente de publicar.

| Campo           | Tipo            | Descripción                                              |
|-----------------|-----------------|----------------------------------------------------------|
| `id`            | `Long`          | PK auto-increment                                        |
| `aggregateType` | `String(100)`   | Nombre del agregado: `"Account"`, `"Transaction"`, etc.  |
| `aggregateId`   | `String`        | UUID del agregado como string                            |
| `eventType`     | `String(100)`   | Nombre del evento: `"AccountCreated"`, `"TransactionCompleted"`, etc. |
| `kafkaTopic`    | `KafkaTopic`    | Enum que mapea al topic de Kafka                         |
| `payload`       | `TEXT`          | JSON del evento (incluye `eventType` dentro del cuerpo)  |
| `status`        | `OutboxStatus`  | Estado del ciclo de vida                                 |
| `createdAt`     | `LocalDateTime` | Seteado automáticamente por `@CreatedDate`               |
| `publishedAt`   | `LocalDateTime` | Seteado al publicar correctamente                        |

**Comportamiento clave**:
- `@PrePersist` → `status = PENDING` automáticamente al persistir
- `markAsPublished()` → `status = PUBLISHED` + `publishedAt = now()`
- `markAsFailed()` → `status = FAILED`
- El constructor no recibe `status` — el estado siempre arranca en PENDING

---

### 3.2 OutboxStatus

**Archivo**: `com.banco.co.outbox.enums.OutboxStatus`

```
PENDING ──▶ PROCESSING ──▶ PUBLISHED
   ▲              │
   │    FAILED ◀──┘  (si Kafka falla)
   └──────────────────── (se reintenta en el próximo ciclo del scheduler)
```

| Estado       | Significado                                                      |
|--------------|------------------------------------------------------------------|
| `PENDING`    | Escrito en la misma transacción del negocio. Esperando al scheduler. |
| `PROCESSING` | El scheduler lo tomó y está publicando (guard anti-TOCTOU)       |
| `PUBLISHED`  | Confirmación del broker Kafka recibida. Terminal exitoso.         |
| `FAILED`     | Kafka rechazó o lanzó excepción. Se reintentará en el próximo ciclo. |

---

### 3.3 KafkaTopic

**Archivo**: `com.banco.co.outbox.enums.KafkaTopic`

| Enum                  | Topic name                    | Productores                        | Consumidores activos        |
|-----------------------|-------------------------------|------------------------------------|-----------------------------|
| `ACCOUNT_EVENTS`      | `banco.account.events`        | AccountService                     | *(ninguno)*                 |
| `TRANSACTION_EVENTS`  | `banco.transaction.events`    | TransactionService                 | FraudDetectionConsumer      |
| `ENVELOPE_EVENTS`     | `banco.envelope.events`       | EnvelopeService, EnvelopeScheduleService | NotificationConsumer  |
| `USER_EVENTS`         | `banco.user.events`           | UserService                        | *(ninguno)*                 |

---

### 3.4 IOutboxEventPort

**Archivo**: `com.banco.co.outbox.port.IOutboxEventPort`

```java
public interface IOutboxEventPort {
    OutboxEvent save(OutboxEvent event);
}
```

Puerto hexagonal que los servicios de negocio usan para escribir al outbox. `IOutboxEventRepository` implementa este puerto, lo que mantiene los servicios de negocio desacoplados de JPA.

---

### 3.5 IOutboxEventRepository

**Archivo**: `com.banco.co.outbox.repository.IOutboxEventRepository`

Extiende `JpaRepository<OutboxEvent, Long>` e implementa `IOutboxEventPort`.

**Métodos clave**:

```java
// 1. Lee hasta 100 filas PENDING o FAILED, ordenadas por createdAt ASC
List<OutboxEvent> findRetryable(List<OutboxStatus> statuses, Pageable pageable);

// 2. Batch UPDATE: PENDING/FAILED → PROCESSING. Solo marca las que siguen en estado retryable.
//    Diseñado para ser seguro ante concurrencia (multi-instancia).
void claimForProcessing(List<Long> ids);

// 3. Re-query para obtener solo las filas que esta instancia realmente ganó.
//    Elimina el race condition TOCTOU en deployments multi-instancia.
List<OutboxEvent> findClaimedForProcessing(List<Long> ids);
```

**Índice en DB**: `(status, created_at)` — optimiza la query de `findRetryable` que filtra por status y ordena por createdAt.

---

### 3.6 OutboxScheduler

**Archivo**: `com.banco.co.outbox.service.OutboxScheduler`

El corazón del outbox. Se ejecuta automáticamente cada 5 segundos.

```java
@Scheduled(fixedDelay = 5000)  // 5 segundos entre ejecuciones
public void processOutbox()
```

**Batch size**: 100 eventos por ciclo.
**Statuses retryable**: `PENDING` y `FAILED`.

**Secuencia de ejecución** (anti-TOCTOU):
```
1. findRetryable([PENDING, FAILED], limit=100)   ← SELECT
2. claimForProcessing(ids)                        ← UPDATE batch → PROCESSING
3. findClaimedForProcessing(ids)                  ← SELECT re-query (solo los que ganó esta instancia)
4. Para cada claimed → kafkaEventPublisher.publish(event)
```

El paso 3 (re-query) es la clave: en un deployment multi-instancia, dos schedulers pueden leer el mismo batch en el paso 1. El UPDATE del paso 2 tiene una condición `WHERE status IN (PENDING, FAILED)`, por lo que solo una instancia gana cada fila. La re-query del paso 3 descarta las que la otra instancia ya reclamó.

---

### 3.7 KafkaEventPublisher

**Archivo**: `com.banco.co.outbox.adapter.KafkaEventPublisher`

Adaptador que toma un `OutboxEvent` y lo publica a Kafka de forma **sincrónica**.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void publish(OutboxEvent event)
```

**Comportamiento**:

| Resultado             | Acción                                        |
|-----------------------|-----------------------------------------------|
| Broker confirma (ack) | `markAsPublished()` → guarda en DB → log INFO |
| `ExecutionException`  | `markAsFailed()` → guarda en DB → log ERROR   |
| `InterruptedException`| `Thread.currentThread().interrupt()` + `markAsFailed()` → log ERROR |

**Puntos importantes**:
- Usa `.get()` — bloqueante hasta recibir ack del broker. Garantiza que si el método retorna sin excepción, el mensaje está en Kafka.
- `REQUIRES_NEW` — cada evento se publica en su propia transacción. Si falla uno, los demás del mismo batch continúan.
- La key del mensaje Kafka es el `aggregateId` (UUID del agregado), lo que garantiza ordering por entidad dentro de un mismo topic.

---

## 4. Esquema de base de datos

**Flyway migration**: `V1__create_outbox_events.sql`

```sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(100)  NOT NULL,
    aggregate_id   VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    kafka_topic    VARCHAR(255)  NOT NULL,
    payload        TEXT          NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at     DATETIME(6)   NOT NULL,
    published_at   DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

El índice `idx_outbox_status_created` es crítico: la query del scheduler filtra por `status IN (PENDING, FAILED)` y ordena por `created_at ASC`. Sin él, sería un full table scan que escala mal.

---

## 5. Productores

### AccountService → `banco.account.events`

| Evento               | Cuándo se publica                             |
|----------------------|-----------------------------------------------|
| `AccountCreated`     | `createAccount()` — nueva cuenta creada       |
| `AccountUpdated`     | `updateAccount()` — datos actualizados        |
| `AccountClosed`      | `closeAccount()` — cierre voluntario (balance=0) |
| `AccountStatusChanged` | `updateAccountStatus()` — admin cambia estado |
| `AccountClosedByAdmin` | `closeAccountByAdmin()` — admin cierra forzosamente |

**Payload ejemplo** (`AccountCreated`):
```json
{
  "eventType": "AccountCreated",
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "accountCode": "ACC-001",
  "accountType": "SAVINGS",
  "status": "ACTIVE",
  "balance": 0
}
```

---

### TransactionService → `banco.transaction.events`

| Evento                  | Cuándo se publica              | Estado implementación |
|-------------------------|--------------------------------|-----------------------|
| `TransactionCompleted`  | `transfer()` — transferencia   | ✅ Implementado        |

> Los demás métodos (`cashDeposit`, `cashWithdrawal`, `checkDeposit`, `payment`, `payService`) lanzan `UnsupportedOperationException`. Cuando se implementen, deben publicar sus eventos correspondientes.

**Payload ejemplo** (`TransactionCompleted`):
```json
{
  "eventType": "TransactionCompleted",
  "transactionId": "7f000101-...",
  "fromAccount": "ACC-001",
  "toAccount": "ACC-002",
  "amount": 5000.00,
  "currency": "CRC"
}
```

---

### EnvelopeService → `banco.envelope.events`

| Evento              | Cuándo se publica                                          |
|---------------------|------------------------------------------------------------|
| `EnvelopeCreated`   | `create()` — envelope nuevo                               |
| `EnvelopeUpdated`   | `update()` — datos actualizados                           |
| `EnvelopeDeposited` | `deposit()` — depósito realizado                          |
| `EnvelopeGoalReached` | `deposit()` — cuando el balance alcanza el `targetAmount` |
| `EnvelopeWithdrawn` | `withdraw()` — retiro realizado                           |
| `EnvelopeDeleted`   | `delete()` — soft delete                                  |

> `EnvelopeGoalReached` se publica **además** de `EnvelopeDeposited` cuando se cumple la meta. El `NotificationConsumer` escucha este evento.

---

### EnvelopeScheduleService → `banco.envelope.events`

Scheduler que corre todos los días a las **3:00 AM** procesando auto-contribuciones.
Publica eventos de auto-contribución por cada envelope procesado exitosamente.

---

### UserService → `banco.user.events`

| Evento              | Cuándo se publica                        |
|---------------------|------------------------------------------|
| `UserCreated`       | Registro de cliente                      |
| `EmployeeCreated`   | Alta de empleado                         |
| `UserUpdated`       | Usuario actualiza sus datos              |
| `PasswordChanged`   | Cambio de contraseña                     |
| `UserDeleted`       | Baja de usuario                          |
| `UserUpdatedByAdmin`| Admin modifica datos de usuario          |
| `UserSuspended`     | Admin suspende usuario                   |
| `UserActivated`     | Admin reactiva usuario                   |
| `UserStatusChanged` | Admin cambia estado                      |

---

## 6. Consumidores

### FraudDetectionConsumer

**Archivo**: `com.banco.co.fraud.adapter.FraudDetectionConsumer`
**Topic**: `banco.transaction.events`
**Group ID**: `banco-service-group`

**Comportamiento**:
1. Parsea el JSON del payload
2. Si `eventType != "TransactionCompleted"` → ignora (log DEBUG)
3. Si `eventType == "TransactionCompleted"` → construye `TransactionFraudContext` y llama `IFraudDetectionService.analyze()`
4. `RuntimeException` propaga hacia Spring Kafka → `DefaultErrorHandler` reintenta hasta 3 veces

**Error handling**:
- `JsonProcessingException` al parsear → log ERROR + return (no retry — payload corrupto)
- `RuntimeException` del servicio → propaga → retry automático

---

### NotificationConsumer

**Archivo**: `com.banco.co.notification.adapter.NotificationConsumer`
**Topic**: `banco.envelope.events`
**Group ID**: `banco-service-group`

**Comportamiento**:
1. Parsea el JSON del payload
2. Si `eventType != "EnvelopeGoalReached"` → ignora (log DEBUG)
3. Si `eventType == "EnvelopeGoalReached"` → log INFO con el mensaje de notificación

> Estado actual: **stub**. Solo loggea. La notificación real (email, push, SMS) no está implementada.

---

## 7. Flujo completo paso a paso

```
Usuario llama POST /api/v1/accounts
         │
         ▼
AccountController → AccountService.createAccount()
         │
         │ @Transactional (misma TX)
         ├── accountRepository.save(account)       ← negocio
         └── outboxEventPort.save(new OutboxEvent(  ← outbox
                 "Account",
                 account.getId(),
                 "AccountCreated",
                 payload,
                 KafkaTopic.ACCOUNT_EVENTS
             ))
         │
         ▼   (TX commit — ambos registros son durables)
    HTTP 201 devuelto al usuario

         ↓ (hasta 5 segundos después)

OutboxScheduler.processOutbox()
  1. SELECT * FROM outbox_events WHERE status IN ('PENDING','FAILED')
     ORDER BY created_at ASC LIMIT 100
  2. UPDATE outbox_events SET status='PROCESSING'
     WHERE id IN (...) AND status IN ('PENDING','FAILED')
  3. SELECT * FROM outbox_events WHERE id IN (...) AND status='PROCESSING'
     [re-query anti-TOCTOU — descarta los que ganó otra instancia]
  4. Para cada fila claimed:
     KafkaEventPublisher.publish(event)

KafkaEventPublisher.publish(event)
  [transacción REQUIRES_NEW]
  kafkaTemplate.send("banco.account.events", aggregateId, payload).get()
  ├── OK  → event.markAsPublished() + save → status=PUBLISHED
  └── ERR → event.markAsFailed()    + save → status=FAILED
                                              (scheduler reintenta próxima ejecución)

         ↓ (si topic = banco.transaction.events y eventType = TransactionCompleted)

FraudDetectionConsumer.consume(payload)
  → fraudDetectionService.analyze(context)
  → log.info resultado

         ↓ (si topic = banco.envelope.events y eventType = EnvelopeGoalReached)

NotificationConsumer.consume(payload)
  → log.info "NOTIFICATION: Envelope X reached savings goal of Y"
```

---

## 8. Garantías y limitaciones actuales

### Garantías

| Garantía                        | Cómo se logra                                              |
|---------------------------------|------------------------------------------------------------|
| At-least-once delivery          | Outbox en misma TX + retry automático de FAILED            |
| Ordering por agregado           | `aggregateId` como key de Kafka — mismo partition          |
| No pérdida ante fallo del app   | DB es durable; scheduler retoma desde PENDING/FAILED       |
| Multi-instancia básica          | TOCTOU resuelto con re-query post-claim                    |
| Publicación sincrónica          | `.get()` — no se marca PUBLISHED hasta que el broker ackea |

### Limitaciones conocidas

| Limitación                      | Impacto                                                    |
|---------------------------------|------------------------------------------------------------|
| Sin retry limit                 | Un evento FAILED puede reintentar indefinidamente          |
| Sin DLQ                         | Eventos envenenados no tienen destino alternativo          |
| Sin limpieza de PUBLISHED       | La tabla crece sin fin con el tiempo                       |
| Sin métricas de lag             | No hay visibilidad de cuántos eventos están acumulados     |
| Sin schema registry             | Payload es JSON libre, sin contrato versionado (Avro, etc.)|
| Sin idempotencia en consumidores| Si el consumer procesa dos veces el mismo evento, duplica la acción |
| Publish síncrono (`.get()`)     | Bloquea el thread del scheduler; con broker lento = throughput bajo |

---

## 9. Pasos a futuro

### 9.1 Consumers pendientes (prioridad alta)

| Topic                  | Consumer faltante          | Propósito                                                      |
|------------------------|----------------------------|----------------------------------------------------------------|
| `banco.account.events` | `AccountEventConsumer`     | Actualizar scoring crediticio, notificar al usuario, sincronizar estado |
| `banco.user.events`    | `UserEventConsumer`        | Onboarding, KYC trigger, auditoría externa                     |

### 9.2 NotificationConsumer — implementar notificaciones reales

Actualmente solo loggea. Pasos para completarlo:
1. Integrar servicio de email (JavaMailSender / SendGrid / SES)
2. Implementar push notifications (Firebase FCM)
3. Extraer a un microservicio `notification-service` que consuma el topic

### 9.3 FraudDetectionConsumer — persistir resultados

El análisis de fraude se calcula pero no se persiste. Falta:
1. `FraudAlertRepository` / tabla `fraud_alerts`
2. Integrar con `AuditLogService` cuando el resultado sea `SUSPICIOUS` o `FRAUDULENT`
3. Bloquear la cuenta automáticamente si el score supera un umbral

### 9.4 Dead Letter Queue (DLQ)

Eventos que fallan repetidamente necesitan un destino alternativo:
```
FAILED (N veces) → DLQ topic → alerta operacional → revisión manual
```
Implementar con `DeadLetterPublishingRecoverer` de Spring Kafka y un límite de reintentos configurable.

### 9.5 Retry limit y poison pills

Agregar un campo `retry_count` a `outbox_events`. Después de N intentos, mover a `DEAD` en lugar de continuar reintentando.

### 9.6 Limpieza de eventos PUBLISHED

Un job de limpieza periódica para archivar o eliminar filas `PUBLISHED` más viejas que N días:
```java
@Scheduled(cron = "0 0 2 * * *")  // 2:00 AM diario
void purgePublishedEvents()  // borra todo lo PUBLISHED con más de 30 días
```

### 9.7 Métricas y observabilidad

Exponer vía Micrometer / Actuator:
- `outbox.events.pending` — gauge del backlog actual
- `outbox.events.failed` — gauge de eventos en error
- `outbox.publish.latency` — histograma de tiempo de publicación
- `outbox.scheduler.batch.size` — cuántos procesa por ciclo

### 9.8 Publish asíncrono (opcional)

Cambiar `.get()` por `ListenableFuture` / `CompletableFuture` para no bloquear el thread del scheduler, aumentando el throughput cuando hay muchos eventos pendientes. Requiere cuidado extra para que `markAsPublished/Failed` se llame en el callback y no pierda la referencia al `OutboxEvent`.

### 9.9 TransactionService — completar métodos

Implementar los métodos que hoy lanzan `UnsupportedOperationException`:

| Método              | Evento a publicar              |
|---------------------|--------------------------------|
| `cashDeposit()`     | `CashDepositCompleted`         |
| `cashWithdrawal()`  | `CashWithdrawalCompleted`      |
| `checkDeposit()`    | `CheckDepositCompleted`        |
| `payment()`         | `PaymentCompleted`             |
| `payService()`      | `ServicePaymentCompleted`      |

### 9.10 Schema registry y versionado de eventos

Considerar migrar los payloads a Avro o Protobuf con Confluent Schema Registry para:
- Contratos explícitos entre producers y consumers
- Evolución de schemas sin romper consumidores existentes
- Validación automática en publish/consume

---

## Estructura de archivos

```
src/main/java/com/banco/co/
│
├── outbox/
│   ├── adapter/
│   │   └── KafkaEventPublisher.java        ← publica a Kafka, maneja ack/fail
│   ├── enums/
│   │   ├── KafkaTopic.java                 ← 4 topics declarados
│   │   └── OutboxStatus.java               ← PENDING, PROCESSING, PUBLISHED, FAILED
│   ├── model/
│   │   └── OutboxEvent.java                ← entidad JPA, tabla outbox_events
│   ├── port/
│   │   └── IOutboxEventPort.java           ← puerto hexagonal (solo save)
│   ├── repository/
│   │   └── IOutboxEventRepository.java     ← JPA + queries claim/retry
│   └── service/
│       └── OutboxScheduler.java            ← @Scheduled cada 5s, batch 100
│
├── fraud/
│   └── adapter/
│       └── FraudDetectionConsumer.java     ← consumer de banco.transaction.events
│
├── notification/
│   └── adapter/
│       └── NotificationConsumer.java       ← consumer de banco.envelope.events (stub)
│
└── resources/db/migration/
    └── V1__create_outbox_events.sql        ← DDL tabla outbox_events
```
