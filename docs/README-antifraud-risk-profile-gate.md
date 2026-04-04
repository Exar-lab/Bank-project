# Antifraud Risk Profile Gate — Arquitectura propuesta

Este documento describe la arquitectura objetivo del cambio **`antifraud-risk-profile-gate`** definido en el flujo SDD.

## Objetivo

Mejorar la decisión antifraude en el camino crítico de autorización manteniendo:

- **Decisión síncrona** (baja latencia y determinística)
- **Enriquecimiento asíncrono** con Kafka (historial y señales de riesgo)

En resumen:

- **Kafka no decide en caliente**.
- **Kafka alimenta el perfil de riesgo**.
- **El gate síncrono consulta un perfil materializado** para decidir `CLEAR | SUSPICIOUS | BLOCKED`.

---

## Principios de diseño

1. **Hexagonal + DDD + feature-first** (`com.banco.co.{feature}.*`)
2. Camino crítico de autorización con latencia controlada
3. Consistencia eventual aceptada solo en enriquecimiento
4. Idempotencia obligatoria en consumidores Kafka
5. Rollout incremental con feature flags y rollback seguro

---

## Arquitectura lógica

### 1) Flujo síncrono (gate de autorización)

`TransactionService.executeFraudGate(...)`:

1. Construye contexto de transacción actual
2. Consulta `RiskProfileQueryPort` (read model materializado)
3. Evalúa reglas (monto + señales históricas)
4. Retorna decisión:
   - `CLEAR`: continúa flujo normal
   - `SUSPICIOUS`: marca para revisión (`PENDING_REVIEW`)
   - `BLOCKED`: bloquea y persiste traza de rechazo

### 2) Flujo asíncrono (enriquecimiento)

Eventos (`TransactionCompleted` y futuros eventos de comportamiento) → Kafka → Consumer antifraude:

1. Consume evento
2. Calcula señales agregadas (velocity, frecuencia, patrones)
3. Actualiza `risk_profiles` (read model)
4. Registra idempotencia de evento procesado

---

## Rol de Kafka en esta arquitectura

Kafka se usa para:

- Propagar eventos de negocio
- Enriquecer perfil de riesgo de forma asíncrona
- Desacoplar productores y procesadores de señales

Kafka **no** se usa como query bus request/reply para el gate de autorización.

---

## Componentes propuestos

### Application / Domain

- `RiskProfileQueryPort` (lectura de perfil para decisión síncrona)
- `RiskProfileGateService` (orquesta evaluación con contexto + perfil)
- Reglas de scoring/umbral (versionables)

### Infrastructure

- `RiskProfileJpaAdapter` (lectura/escritura MySQL)
- `RiskProfileConsumer` (Kafka listener)
- `risk_profile_event_processing` (tabla de idempotencia)

### Data Model (propuesto)

- `risk_profiles`
  - `account_id` / `customer_id`
  - métricas agregadas (conteos, montos, ventanas)
  - señales de riesgo actuales
  - `updated_at`, `version`
- `risk_profile_event_processing`
  - `event_id`
  - `processed_at`
  - estado de procesamiento

---

## Manejo de errores y resiliencia (Kafka)

Buenas prácticas aplicadas:

- `DefaultErrorHandler`
- `DeadLetterPublishingRecoverer`
- Excepciones de deserialización como no-retryable
- Reintentos controlados + DLT
- Actualización idempotente del perfil

### Política explícita del consumer `RiskProfileConsumer`

- **Se descartan (sin retry / sin DLT)**
  - JSON malformado
  - Campos requeridos faltantes (`eventId` / `transactionId`, `fromAccountCode`)
  - `amount` no parseable
  - Eventos no relevantes para perfil (`eventType != TransactionCompleted`)
- **Se propagan (con retry + eventual DLT)**
  - Fallos procesables/transitorios del updater (DB timeout, lock contention, errores runtime de infraestructura)

Esta política se implementa en `RiskProfileConsumer.consume(...)`:
- errores de payload inválido → `return` con log `risk_profile_async_invalid_payload`
- errores procesables → `throw IllegalStateException(...)` para activar `DefaultErrorHandler`

### Operación de retry / DLT

- Error handler global Kafka: `DefaultErrorHandler` con `FixedBackOff(1000ms, 3)`
- Recoverer: `DeadLetterPublishingRecoverer` a tópico `{topic}.DLT` conservando partición
- Para `banco.transaction.events`, el DLT resultante es `banco.transaction.events.DLT`

### Métricas y logs operacionales

- Métrica principal async:
  - `fraud.riskprofile.async.events{outcome=processed|duplicated|invalid_payload|failed}`
- Logs estructurados esperados:
  - `event=risk_profile_async_processed`
  - `event=risk_profile_async_duplicated`
  - `event=risk_profile_async_invalid_payload`
  - `event=risk_profile_async_failed`
  - `event=kafka_retry_attempt`
  - `event=kafka_dlt_publish`

---

## Estrategia de pruebas (batch 3)

Se ejecutan pruebas focalizadas (sin build completo):

1. **Unitarias del consumer**
   - `RiskProfileConsumerTest`
   - Verifica rutas de `processed`, `duplicated`, `invalid_payload` y `failed`.
2. **Unitarias del updater async**
   - `RiskProfileAsyncUpdaterServiceTest`
   - Verifica idempotencia, thresholds y `ruleSetVersion`.
3. **Integración Kafka DLT end-to-end real**
   - `RiskProfileConsumerDltIntegrationTest`
   - Flujo validado: mensaje válido → falla procesable simulada → retries automáticos → publicación en `banco.transaction.events.DLT`.

---

## Troubleshooting

### 1) `UnsupportedClassVersionError` en tests por preview features

Síntoma: tests de integración fallan en runtime porque la JVM de tests no corre con `--enable-preview`.

Solución aplicada en Maven:
- `maven-compiler-plugin` compila con `--enable-preview` y `release` alineado a `java.version`.
- `maven-surefire-plugin` y `maven-failsafe-plugin` ejecutan tests con `argLine=--enable-preview`.

### 2) No aparecen mensajes en DLT

Checklist rápido:
- Confirmar que la excepción sea **propagada** (no capturada con `return`).
- Confirmar tópico base y DLT: `banco.transaction.events` → `banco.transaction.events.DLT`.
- Revisar logs `kafka_retry_attempt` y `kafka_dlt_publish`.

### 3) Reintentos no ocurren

- Verificar `containerFactory = kafkaListenerContainerFactory` en el listener.
- Confirmar `DefaultErrorHandler` activo en `KafkaConsumerConfig`.
- Verificar `AckMode.RECORD` para commits por registro exitoso.

---

## Estrategia de rollout

1. **Fase 0**: Persistir perfil en sombra (sin afectar decisión)
2. **Fase 1**: Activar consulta de perfil en gate con feature flag
3. **Fase 2**: Ajustar reglas/umbrales con métricas reales
4. **Rollback**: desactivar flag y volver al gate actual por umbral de monto

---

## Observabilidad y SLOs sugeridos

- Latencia p95 del gate síncrono (objetivo: < 50 ms)
- Lag de consumer Kafka
- Ratio `CLEAR/SUSPICIOUS/BLOCKED`
- Tasa de errores y envíos a DLT
- Tasa de deduplicación idempotente

---

## Política de timeout y fallback del gate síncrono

Para cerrar los gaps de verify CRITICAL, el gate síncrono de `RiskProfileGateService` implementa ahora timeout controlado + fallback explícito:

- **Timeout configurable**: `fraud.detection.risk-profile-query-timeout-ms` (default `25ms`).
- **Policy configurable**: `fraud.detection.risk-profile-fallback-policy`.
- **Policy actual (recomendada y activa)**: `FAIL_OPEN_LEGACY_THRESHOLD`.

### Comportamiento exacto

1. Se intenta leer profile por cuenta con timeout.
2. Si la lectura responde dentro del timeout:
   - con profile `HIGH/RESTRICTED` => `BLOCKED`
   - con profile `MEDIUM` => `SUSPICIOUS`
   - `LOW` => reglas legacy por monto
3. Si hay **timeout/error/interrupción** en la consulta:
   - se aplica fallback **fail-open** contra umbrales legacy por monto (`suspicious-threshold`, `blocked-threshold`)
   - se loguea evento explícito de fallback
   - se incrementa métrica por razón

### Métricas

- `fraud.riskprofile.gate.fallback{reason=timeout|error}`
- `fraud.riskprofile.gate.latency`

### Evidencia NFR1 (<50ms)

Se agregó test micro determinístico sin I/O real que valida latencia del gate normal `<50ms` y registro del timer:

- `RiskProfileGateServiceTest#testEvaluate_NormalPath_LatencyUnderFiftyMillisecondsAndTimerRecorded`

Y test de degradación con timeout + fallback funcional + métrica:

- `RiskProfileGateServiceTest#testEvaluate_QueryTimeout_AppliesFailOpenFallbackAndIncrementsTimeoutMetric`

---

## Riesgos técnicos identificados

- **Staleness** por consistencia eventual del perfil
- Condiciones de carrera en actualizaciones concurrentes
- Duplicados por reintentos sin idempotencia robusta
- Impacto de latencia si la consulta del perfil no está indexada

---

## Decisión de storage inicial

Para esta iteración:

- **MySQL materializado** como fuente de verdad del perfil
- Diseño preparado para introducir Redis más adelante como capa de cache/read-optimization

---

## Estado SDD actual

Completado:

- Explore ✅
- Proposal ✅
- Spec ✅
- Design ✅
- Tasks ✅

Pendiente:

- Apply ⏳

---

## Apply checklist (batch 3)

- [x] Retry + DLT configurados y trazables en consumer de risk profile
- [x] Manejo consistente de errores:
  - [x] Errores no-procesables (payload inválido / campos faltantes / amount inválido) se descartan sin retry
  - [x] Errores procesables (fallo en updater/DB) escalan para retry y eventual DLT
- [x] Observabilidad async:
  - [x] Logs estructurados para `processed`, `duplicated`, `invalid_payload`, `failed`
  - [x] Métricas Micrometer básicas por outcome (`fraud.riskprofile.async.events`)
- [x] Endurecimiento operacional:
  - [x] Umbrales/scores/version/consumerName parametrizados en properties
- [x] Tests focalizados batch 3 (sin build completo)
  - [x] `RiskProfileConsumerTest`
  - [x] `RiskProfileAsyncUpdaterServiceTest`
  - [x] `RiskProfileConsumerDltIntegrationTest`
