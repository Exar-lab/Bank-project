# Email Notification System

Sistema de envío de correos durable y auditable implementado mediante el **Email Outbox Pattern**.

---

## Por qué Outbox Pattern

Enviar un email directamente desde un consumer de Kafka o desde un service introduce un problema clásico: si el SMTP falla, el mensaje Kafka ya fue consumido y el correo nunca se reintenta. Si el SMTP tarda, bloquea el thread del consumer.

El Outbox Pattern resuelve esto en dos pasos:

1. **Encolar** la intención de envío en una tabla de base de datos, dentro de la misma transacción del evento de negocio.
2. **Despachar** de forma asíncrona desde un proceso separado (`EmailOutboxRelay`), con reintentos y lifecycle propio.

El resultado es que la escritura en la tabla es atómica con el evento, y el envío SMTP es independiente y resiliente.

---

## Flujo completo

```
Evento de negocio (Kafka)
         │
         ▼
┌─────────────────────┐
│  Kafka Consumer     │  UserEventsConsumer          → evento UserCreated
│  (adapter/in)       │  EnvelopeEventsEmailHandler  → evento EnvelopeGoalReached
└────────┬────────────┘
         │  llama a
         ▼
┌─────────────────────┐
│   EmailServiceImpl  │  Serializa el contexto del template a JSON
│   (service)         │  Inserta fila en email_outbox_events con status=PENDING
└────────┬────────────┘  (si event_id ya existe → dedup silencioso, no lanza excepción)
         │
         │  (transacción del consumer termina, Kafka avanza offset)
         │
         │  cada N segundos (banco.mail.relay.poll-delay-ms)
         ▼
┌─────────────────────┐
│  EmailOutboxRelay   │  1. Selecciona IDs con status=PENDING y available_at <= now
│  (@Scheduled)       │     usando FOR UPDATE SKIP LOCKED  →  safe en multi-instancia
└────────┬────────────┘  2. Marca filas como status=PROCESSING, claimed_by={owner}
         │               3. Encola cada ID en el thread pool (emailDispatcherExecutor)
         ▼
┌─────────────────────┐
│ EmailOutboxDispatch │  Cada tarea del pool, en su propia transacción (REQUIRES_NEW):
│ Worker              │
│  (relay)            │  ┌─ Éxito ──────────────────────────────────────────────────┐
│                     │  │  renderiza template Thymeleaf                            │
│                     │  │  envía via JavaMailSender (SMTP)                         │
│                     │  │  status → SENT, sentAt = now, claimedBy = null           │
│                     │  │  audit: logSent(userId, recipientHash)                   │
│                     │  └──────────────────────────────────────────────────────────┘
│                     │
│                     │  ┌─ Fallo (NotificationException / JsonProcessingException) ┐
│                     │  │  attemptCount++                                          │
│                     │  │  si attemptCount < maxAttempts:                          │
│                     │  │    status → PENDING, availableAt = now + backoff         │
│                     │  │    backoff = max(60s, 2^attempt × 60s)                   │
│                     │  │  si attemptCount >= maxAttempts:                         │
│                     │  │    status → DEAD                                         │
│                     │  │    audit: logDead(eventId, attempts, templateName)       │
│                     │  └──────────────────────────────────────────────────────────┘
└─────────────────────┘
```

---

## Eventos que disparan correos

### `UserCreated` → Correo de bienvenida

**Topic**: `banco.user.events`  
**Consumer**: `UserEventsConsumer`  
**Template**: `email/welcome`  
**Asunto**: `Bienvenido/a a Banco CO`

```json
{
  "eventType": "UserCreated",
  "eventId":   "uuid-del-evento",
  "userId":    "uuid-del-usuario",
  "email":     "usuario@ejemplo.com",
  "recipientName": "Juan Pérez",
  "userCode":  "USR-00123"
}
```

El consumer extrae `recipientName`, `userCode` y `bankName` como contexto del template.

---

### `EnvelopeGoalReached` → Correo de meta alcanzada

**Topic**: `banco.envelope.events`  
**Consumer**: `EnvelopeEventsEmailHandler`  
**Template**: `email/envelope-goal-reached`  
**Asunto**: `Has alcanzado tu meta de ahorro`

```json
{
  "eventType":    "EnvelopeGoalReached",
  "eventId":      "uuid-del-evento",
  "userId":       "uuid-del-usuario",
  "envelopeCode": "ENV-00456",
  "goalAmount":   "5000.00"
}
```

El handler busca al usuario en base de datos para obtener email y nombre.

---

## Tabla `email_outbox_events`

```sql
CREATE TABLE email_outbox_events (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id             VARCHAR(36)  UNIQUE NOT NULL,   -- dedup key
    user_id              BINARY(16)   NOT NULL,
    recipient_email      VARCHAR(254) NOT NULL,
    recipient_name       VARCHAR(200) NOT NULL,
    template_name        VARCHAR(100) NOT NULL,
    template_context_json TEXT        NOT NULL,
    subject              VARCHAR(998) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count        INT          NOT NULL DEFAULT 0,
    available_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at              DATETIME(6)  NULL,
    last_error           TEXT         NULL,
    created_at           DATETIME(6)  NOT NULL,
    claimed_by           VARCHAR(100) NULL,
    INDEX idx_email_outbox_status_available (status, available_at)
);
```

### Estados posibles

```
PENDING ──(relay lo toma)──► PROCESSING ──(SMTP OK)──► SENT
                                  │
                                  └──(fallo, attempts < max)──► PENDING (con backoff)
                                  │
                                  └──(fallo, attempts >= max)──► DEAD
```

---

## Deduplicación

`event_id` tiene un `UNIQUE KEY` en base de datos. Si el mismo evento Kafka llega dos veces (at-least-once delivery), `EmailServiceImpl` captura la `DataIntegrityViolationException` y la descarta silenciosamente registrando solo un warning en log. **No se envían correos duplicados.**

---

## Seguridad multi-instancia (`FOR UPDATE SKIP LOCKED`)

El `EmailOutboxRelay` usa `FOR UPDATE SKIP LOCKED` al seleccionar filas pendientes. Esto garantiza que si hay múltiples instancias del servicio corriendo en paralelo, cada fila es reclamada por exactamente una instancia. No hay condición de carrera.

El campo `claimed_by` identifica qué instancia reclamó cada fila (formato: `{appName}@{hostname}-{uuid-suffix}`).

---

## Protección de datos personales (PII)

El email del destinatario **nunca aparece en logs ni en auditoría**. `RecipientHasher` genera un hash SHA-256 del email y ese hash es lo único que se registra.

```java
// Solo esto llega a los logs de auditoría:
emailAuditPublisher.logSent(userId, recipientHasher.hash(recipientEmail));
```

---

## Feature flag

La propiedad `banco.mail.enabled` controla si el sistema envía correos realmente:

| Valor | Comportamiento |
|-------|---------------|
| `true` | `JavaMailEmailDispatcher` — envía via SMTP real |
| `false` | `NoOpEmailDispatcher` — no envía, la fila vuelve a `PENDING` |

Útil para ambientes locales o de testing sin servidor SMTP.

---

## Configuración relevante

```yaml
banco:
  mail:
    enabled: true
    host: smtp.ejemplo.com
    port: 587
    username: usuario@ejemplo.com
    password: ${MAIL_PASSWORD}
    from: noreply@banco.co
    relay:
      batch-size: 10          # filas por ciclo del relay
      poll-delay-ms: 10000    # cada cuánto corre el relay (ms)
      max-attempts: 5         # intentos antes de pasar a DEAD
    executor:
      core-pool-size: 2
      max-pool-size: 5
      queue-capacity: 50
```

---

## Cómo agregar un nuevo tipo de correo

1. **Crear el DTO de contexto** en `notification/email/dto/`:
   ```java
   public record MiNuevoEmailContext(String campo1, String campo2) {}
   ```

2. **Crear el template Thymeleaf** en `resources/templates/email/`:
   ```
   mi-nuevo-email.html
   ```

3. **Agregar el consumer o handler** en `notification/email/adapter/in/`:
   ```java
   emailService.enqueue(eventId, userId, email, name, "email/mi-nuevo-email", context, "Asunto");
   ```

El relay, el retry, la deduplicación y la auditoría funcionan automáticamente para cualquier fila nueva en `email_outbox_events`.

---

## Clases principales

| Clase | Responsabilidad |
|-------|----------------|
| `EmailServiceImpl` | Encola intenciones de envío en la tabla outbox |
| `EmailOutboxRelay` | Scheduler que toma filas PENDING y las pasa a PROCESSING |
| `EmailOutboxDispatchWorker` | Renderiza template, envía SMTP, actualiza estado |
| `JavaMailEmailDispatcher` | Adaptador SMTP real (JavaMailSender) |
| `NoOpEmailDispatcher` | Adaptador vacío para feature flag apagado |
| `ThymeleafEmailTemplateRenderer` | Renderizado de templates HTML con Thymeleaf |
| `RecipientHasher` | SHA-256 del email para logs y auditoría (PII safe) |
| `EmailAuditPublisher` | Publica eventos de auditoría (SENT, DEAD, DEDUPED) |
| `UserEventsConsumer` | Kafka consumer para `UserCreated` → welcome email |
| `EnvelopeEventsEmailHandler` | Kafka consumer para `EnvelopeGoalReached` → meta email |
