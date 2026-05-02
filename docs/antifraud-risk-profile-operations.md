# Antifraud Risk Profile Gate — Runbook Operativo

## Alcance

Runbook corto para operar el consumer async de perfil de riesgo (`RiskProfileConsumer`) y su circuito de retry/DLT.

## Flujo operativo

1. Evento entra por `banco.transaction.events`.
2. `RiskProfileConsumer` valida payload.
3. Si payload inválido, se **descarta** y se incrementa `invalid_payload`.
4. Si payload válido, delega en `RiskProfileAsyncUpdaterService`.
5. Si falla procesamiento (runtime/transitorio), se **propaga excepción**.
6. `DefaultErrorHandler` hace retries y, al agotar intentos, publica en `banco.transaction.events.DLT`.

## Política de errores

### No-procesables (discard)
- JSON malformado
- Campos requeridos ausentes
- `amount` inválido

Acción: `skip` sin retry y sin DLT.

### Procesables/transitorios (retry + DLT)
- Errores runtime del updater (ej. timeout DB)

Acción: throw para activar `DefaultErrorHandler`.

## Configuración relevante

- `KafkaConsumerConfig.defaultErrorHandler()`
  - backoff: `FixedBackOff(1000ms, 3)`
  - DLT: `<topic>.DLT`
- `RiskProfileConsumer` usa `containerFactory = kafkaListenerContainerFactory`

## Métricas clave

- `fraud.riskprofile.async.events{outcome=processed}`
- `fraud.riskprofile.async.events{outcome=duplicated}`
- `fraud.riskprofile.async.events{outcome=invalid_payload}`
- `fraud.riskprofile.async.events{outcome=failed}`

## Logs clave

- `event=risk_profile_async_processed`
- `event=risk_profile_async_duplicated`
- `event=risk_profile_async_invalid_payload`
- `event=risk_profile_async_failed`
- `event=kafka_retry_attempt`
- `event=kafka_dlt_publish`

## Pruebas de verificación recomendadas

1. `RiskProfileConsumerTest`
2. `RiskProfileAsyncUpdaterServiceTest`
3. `RiskProfileConsumerDltIntegrationTest`

## Troubleshooting express

### No hay DLT tras errores
- Confirmar que el error se propaga (sin `return` silencioso).
- Confirmar que existe el tópico `banco.transaction.events.DLT`.
- Revisar logs de `kafka_retry_attempt` y `kafka_dlt_publish`.

### Error `UnsupportedClassVersionError` en tests
- Verificar que Maven y CI usan el mismo JDK declarado en `java.version`.
- GitHub Actions debe instalar Java 24 para compilar y ejecutar tests con `maven.compiler.release=24`.
