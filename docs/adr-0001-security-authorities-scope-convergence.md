# ADR-0001: Convergencia de naming de authorities/scopes en controllers

- **Status**: Accepted
- **Date**: 2026-04-05
- **Decision Makers**: Backend team
- **Related SDD Change**: `complete-remaining-entity-controllers-scopes` (post-cierre)

## Contexto

El servicio actualmente opera con compatibilidad dual de authorities en controladores (`x:*` legacy y `SCOPE_x:*` moderno) para evitar ruptura de clientes/token emitters existentes.

Esto resolvió la migración incremental, pero introduce drift y costo operativo:

- Reglas duplicadas en `@PreAuthorize`.
- Mayor superficie para errores de seguridad por naming inconsistente.
- Dificultad para auditar permisos efectivos por endpoint.

Adicionalmente, los security slice tests de controllers estaban incompletos para Envelope, lo que debilitaba detección temprana de regresiones en la matriz 200/403/401.

## Decisión

Adoptar `SCOPE_x:*` como naming canónico para autorización a nivel controller y definir un plan explícito de deprecación de authorities legacy `x:*`.

### Regla canónica

- **Canónico**: `SCOPE_<recurso>:<accion>`
  - Ejemplo: `SCOPE_envelope:read`, `SCOPE_envelope:write`.
- **Legacy temporal**: `<recurso>:<accion>`
  - Ejemplo: `envelope:read`, `envelope:write`.

## Plan de deprecación (`x:*` -> `SCOPE_x:*`)

### Fase 0 — Baseline (actual)

- Mantener dual authorities en endpoints activos para continuidad.
- Validar matriz de seguridad con slice tests focalizados.

### Fase 1 — Emisión y observabilidad

- Asegurar que issuer(s) de JWT emitan `SCOPE_x:*` para todos los clientes nuevos.
- Instrumentar métricas/logs de uso de authorities legacy vs canónicas (sin exponer secretos).
- Publicar comunicación interna/externa de deprecación con fecha objetivo.

### Fase 2 — Soft deprecation

- Mantener compatibilidad en runtime, pero marcar `x:*` como deprecated en documentación y revisiones.
- Rechazar introducción de nuevos endpoints con authorities legacy.
- Enforce en CI con suites focalizadas de controller/security para prevenir drift.

### Fase 3 — Hard deprecation

- Remover `x:*` de expresiones `@PreAuthorize` una vez que métricas confirmen adopción completa de `SCOPE_x:*`.
- Ejecutar campaña de verificación regresiva con suites focalizadas y smoke de autenticación.

## Consecuencias

### Positivas

- Modelo de seguridad consistente y auditable.
- Menor riesgo de drift en anotaciones de autorización.
- Mejor señal temprana en CI ante regresiones de permisos.

### Negativas / Trade-offs

- Sobrecosto temporal al sostener dualidad durante transición.
- Requiere coordinación con equipos de emisión de tokens/clientes.

## Implementación asociada en este post-cierre

1. Se agrega `EnvelopeControllerSecuritySliceWebMvcTest` con matriz representativa 200/403/401.
2. Se agrega ejecución en CI de suites focalizadas de controller/security para detectar drift.

## Criterios de salida de deprecación

- 0 tráfico con authorities legacy `x:*` durante la ventana acordada.
- 100% de endpoints críticos con `SCOPE_x:*` únicamente.
- Suites focalizadas de controller/security en verde de forma sostenida.
