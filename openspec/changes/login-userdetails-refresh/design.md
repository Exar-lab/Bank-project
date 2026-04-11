# Design: Login, UserDetailsService, and Stateful Refresh Token

## Technical Approach

Implement an `auth` vertical slice (`com.banco.co.auth.*`) that plugs into existing Spring Security (`SecurityFilterChain` + `AuthenticationManager`) and makes `refresh_tokens` the single source of truth for refresh lifecycle. The flow will: (1) authenticate with `UserDetailsService`, (2) issue JWT access/refresh, (3) persist refresh state, (4) rotate on refresh, (5) detect reuse and revoke active sessions.

## Architecture Decisions

### Decision: Refresh token source of truth migration

| Option | Tradeoff | Decision |
|---|---|---|
| Keep `user_credentials.refreshToken*` + table | Ambiguous state, hard reuse detection | ❌ |
| Use only `refresh_tokens` with phased migration | Extra migration work, clear invariants | ✅ |

**Choice**: Move to `refresh_tokens` as canonical store.
**Alternatives considered**: Keep dual model permanently.
**Rationale**: Required for deterministic rotation/revocation and to avoid drift between entity and legacy columns.

### Decision: Refresh lookup strategy

| Option | Tradeoff | Decision |
|---|---|---|
| Store raw token in DB | Simpler queries, higher leak impact | ❌ |
| Store token hash + jti (`sha256`) | More code, lower compromise blast radius | ✅ |

**Choice**: Persist `tokenHash` and `jti` (from JWT ID); never persist raw refresh token.
**Alternatives considered**: Raw token persistence.
**Rationale**: Security hardening with minimal runtime cost.

### Decision: Reuse detection scope

| Option | Tradeoff | Decision |
|---|---|---|
| Revoke only current token chain | Lower disruption, weaker incident response | ❌ |
| Revoke all active user refresh tokens | User re-login on all devices | ✅ |

**Choice**: On detected reuse of revoked token, revoke all active tokens for that user.
**Alternatives considered**: Family-only revocation.
**Rationale**: Spec requires strong mitigation for suspected theft.

## Data Flow

```text
LOGIN
AuthController -> AuthService -> AuthenticationManager
                      -> UserDetailsService (JOIN FETCH roles+permissions)
                      -> JwtUtils (access + refresh)
                      -> RefreshTokenPort.save(active)

REFRESH
AuthController -> AuthService.refresh(refreshToken)
  -> JwtUtils.validateRefreshToken
  -> RefreshTokenPort.findByJtiForUpdate
  -> if ACTIVE: revoke(old, reason=ROTATED) + create(new)
  -> if REVOKED: revokeAllByUser(reason=REUSE_DETECTED) + 401
  -> return new pair

LOGOUT
AuthController -> AuthService.logout(refreshToken)
  -> validate + find active token
  -> revoke(reason=LOGOUT)
```

## Component Mapping by Layer

- **Presentation**: `auth/controller/AuthController` (`/api/v1/auth/login|refresh|logout`), `@Validated`, no business logic.
- **Application**: `auth/service/AuthService`, `auth/service/CustomUserDetailsService`, DTO records, mappers.
- **Domain**: `security/token/model/RefreshToken` lifecycle (`active/revoked/expired/reused` semantics), auth exceptions.
- **Infrastructure**: JPA repository queries with `@Transactional(readOnly = true)` on reads; migration scripts; `JwtUtils` integration.

## File Changes

| File | Action | Description |
|---|---|---|
| `banco-service/src/main/java/com/banco/co/auth/controller/AuthController.java` | Create | Login/refresh/logout endpoints. |
| `banco-service/src/main/java/com/banco/co/auth/service/AuthService.java` | Create | Orchestrates login/refresh/logout use cases. |
| `banco-service/src/main/java/com/banco/co/auth/service/CustomUserDetailsService.java` | Create | Loads user with authorities for `AuthenticationManager`. |
| `banco-service/src/main/java/com/banco/co/auth/dto/*.java` | Create | `record` DTOs for requests/responses. |
| `banco-service/src/main/java/com/banco/co/user/repository/IUserCredential.java` | Modify | Add `JOIN FETCH` query for credential + roles + permissions. |
| `banco-service/src/main/java/com/banco/co/security/token/model/RefreshToken.java` | Modify | Add `@EntityListeners`, `tokenHash`, `jti`, family/rotation fields, revoke metadata. |
| `banco-service/src/main/java/com/banco/co/security/token/repository/IRefreshTokenRepository.java` | Modify | Add lock/read queries + bulk revoke methods. |
| `banco-service/src/main/java/com/banco/co/user/model/adapter/SecurityUser.java` | Modify | Fix `isAccountNonLocked()` inversion bug. |
| `banco-service/src/main/java/com/banco/co/user/model/UserCredential.java` | Modify | Mark legacy `refreshToken*` deprecated for migration; then remove in phase 2. |
| `banco-service/src/main/resources/db/migration/V7__create_refresh_tokens_table.sql` | Create | Canonical refresh table and indexes. |
| `banco-service/src/main/resources/db/migration/V8__migrate_legacy_refresh_tokens.sql` | Create | Backfill/cleanup to canonical table. |
| `banco-service/src/main/resources/db/migration/V9__drop_legacy_refresh_columns.sql` | Create | Final removal after cutover validation. |

## Interfaces / Contracts

```java
public record LoginRequestDto(String email, String password) {}
public record RefreshRequestDto(String refreshToken) {}
public record TokenPairResponseDto(String accessToken, String refreshToken, String tokenType, long expiresIn) {}

public interface RefreshTokenPort {
  Optional<RefreshToken> findByJtiForUpdate(String jti);
  RefreshToken save(RefreshToken token);
  void revoke(UUID tokenId, RevocationReason reason);
  void revokeAllActiveByUser(UUID userId, RevocationReason reason);
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Rotation, reuse detection, lock-state behavior | JUnit5 + Mockito on `AuthService` and `CustomUserDetailsService`. |
| Integration | Repository fetch (`JOIN FETCH`), transactional revoke/update, migrations | `@DataJpaTest` + Testcontainers MySQL. |
| E2E/API | 200/401/403 contracts for login/refresh/logout | `@WebMvcTest` controller slices + security config imports. |

## Migration / Rollout

Phase 1: introduce auth endpoints and canonical table; write only to `refresh_tokens`; read fallback from legacy columns only during migration window.  
Phase 2: run backfill (`V8`) and monitor zero legacy reads.  
Phase 3: drop legacy columns (`V9`) and remove fallback path.  
No feature flag required; rollout is DB-version gated.

## Open Questions

- [ ] Should logout endpoint return `200` (body) or `204` (empty) as team API convention?
