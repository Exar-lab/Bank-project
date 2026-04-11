## Exploration: login-userdetails-refresh

### Current State
- El proyecto ya tiene base de seguridad JWT en modo stateless (`SecurityFilterChain` + `JwtTokenValidator`) y autorización por `@PreAuthorize` en controllers.
- Existe utilitario JWT con emisión/validación de access y refresh (`JwtUtils`), con claims de roles/scopes para access token.
- Existe modelo stateful para refresh (`RefreshToken` + `IRefreshTokenRepository`), pero no está integrado en ningún flujo de aplicación (no hay service/controller de auth/login/refresh).
- No existe implementación de `UserDetailsService` ni flujo de `AuthenticationManager.authenticate(...)` en código productivo.
- No hay endpoints de auth/login/refresh en controllers ni en OpenAPI actual.

### Affected Areas
- `banco-service/src/main/java/com/banco/co/security/config/SecurityConfig.java` — wiring de seguridad, filtros y beans de autenticación.
- `banco-service/src/main/java/com/banco/co/security/config/filter/JwtTokenValidator.java` — autenticación por bearer token en cada request.
- `banco-service/src/main/java/com/banco/co/utils/JwtUtils.java` — emisión/validación de JWT access/refresh.
- `banco-service/src/main/java/com/banco/co/user/model/adapter/SecurityUser.java` — adaptación `UserDetails` y authorities.
- `banco-service/src/main/java/com/banco/co/user/model/UserCredential.java` — credenciales, estado de cuenta y campos legacy de refresh.
- `banco-service/src/main/java/com/banco/co/user/repository/IUserCredential.java` — lookup de usuario por email para login.
- `banco-service/src/main/java/com/banco/co/security/token/model/RefreshToken.java` — persistencia stateful por token.
- `banco-service/src/main/java/com/banco/co/security/token/repository/IRefreshTokenRepository.java` — queries para refresh token lifecycle.
- `docs/api/openapi.yaml` y `docs/diagrams/auth-flow.md` — documentación, hoy sin contrato de login/refresh.

### Approaches
1. **Stateful refresh mínimo (MVP)** — agregar login + refresh usando tabla `refresh_tokens`, revocación por token y emisión de nuevo access.
   - Pros: entrega rápida, alinea con decisión explícita stateful DB.
   - Cons: menor robustez ante replay/reuse si no hay rotación estricta ni detección de reuse.
   - Effort: Medium.

2. **Stateful refresh robusto con rotación y reuse detection** — login + refresh con rotación obligatoria por uso, revocación de familia/sesión y telemetría de seguridad.
   - Pros: mejor postura de seguridad, control de sesiones/dispositivos, camino limpio para logout global/selectivo.
   - Cons: más lógica de dominio (familias, invalidación en cascada, manejo de concurrencia).
   - Effort: High.

### Recommendation
Implementar **Approach 2** en forma incremental (primero contrato y dominio base, luego hardening):
1) `UserDetailsService` + provider para login real con `AuthenticationManager`.
2) Endpoints `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`.
3) Persistencia stateful en `refresh_tokens` con rotación por refresh, revoke de token previo y política de reuse.
4) Limpiar ambigüedad entre campos refresh en `user_credentials` vs entidad `refresh_tokens` (elegir una sola fuente de verdad: tabla `refresh_tokens`).

### Risks
- **Bug funcional detectado**: `SecurityUser.isAccountNonLocked()` está invertido y devuelve `!user.isAccountNonLocked()`; rompe semántica de bloqueo.
- **Auditoría incompleta en RefreshToken**: usa `@CreatedDate` sin `@EntityListeners(AuditingEntityListener.class)`, riesgo de `createdAt` nulo.
- **Gap de esquema**: no se observa migración Flyway de `refresh_tokens` en `src/main/resources/db/migration` del repo actual.
- **Ambigüedad de modelo**: conviven `UserCredential.refreshToken`/`refreshTokenExpiry` y entidad `RefreshToken`, riesgo de inconsistencias.
- **Lazy loading para authorities**: un `UserDetailsService` naïve puede disparar `LazyInitializationException` si no hace fetch de roles/permisos dentro de transacción de lectura.
- **Cobertura de tests faltante**: no hay tests para login/refresh/logout ni contrato OpenAPI asociado.

### Ready for Proposal
Yes — listo para pasar a `sdd-propose` con foco en: contrato API auth, diseño de rotación/revocación stateful, migraciones DB faltantes y plan de compatibilidad/documentación.
