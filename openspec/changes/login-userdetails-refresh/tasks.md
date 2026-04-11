# Tasks: Login, UserDetailsService, and Stateful Refresh Token

## Phase 1: Foundation + Migration Base (Infra/Domain)

- [x] 1.1 Confirm API convention for logout (`200` vs `204`) and lock it for controller/tests (`openspec/changes/login-userdetails-refresh/design.md` open question).
- [x] 1.2 Create `banco-service/src/main/resources/db/migration/V7__create_refresh_tokens_table.sql` with canonical columns (`jti`, `token_hash`, status, revoke metadata, `created_at`) and indexes.
- [x] 1.3 Update `banco-service/src/main/java/com/banco/co/security/token/model/RefreshToken.java` to add `@EntityListeners(AuditingEntityListener.class)` and `createdAt` auditing fix.
- [x] 1.4 Extend `RefreshToken` model with lifecycle fields (`jti`, `tokenHash`, `revokedAt`, `revocationReason`, rotation/family refs) and remove raw-token persistence.
- [x] 1.5 Modify `banco-service/src/main/java/com/banco/co/security/token/repository/IRefreshTokenRepository.java` with lock/read + bulk revoke queries; mark read queries `@Transactional(readOnly = true)`.

## Phase 2: Application/Auth Core + Security Fixes

- [x] 2.1 Fix lock-state bug in `banco-service/src/main/java/com/banco/co/user/model/adapter/SecurityUser.java` (`isAccountNonLocked()` must match real account lock state).
- [x] 2.2 Add auth DTO records in `banco-service/src/main/java/com/banco/co/auth/dto/` (`LoginRequestDto`, `RefreshRequestDto`, `TokenPairResponseDto`).
- [x] 2.3 Modify `banco-service/src/main/java/com/banco/co/user/repository/IUserCredential.java` with single `JOIN FETCH` (+ `DISTINCT` when needed) for credentials + roles/authorities.
- [x] 2.4 Create `banco-service/src/main/java/com/banco/co/auth/service/CustomUserDetailsService.java` using repository query from 2.3 and mapping lock/authorities correctly.
- [x] 2.5 Create `banco-service/src/main/java/com/banco/co/auth/service/AuthService.java` for login, refresh rotation, logout revocation, and reuse-detection (`revokeAllActiveByUser`).
- [x] 2.6 Integrate JWT refresh parsing to use `jti` + `sha256(token)` validation path; persist only hash/jti in `refresh_tokens`.

## Phase 3: Presentation + Security Wiring

- [x] 3.1 Create `banco-service/src/main/java/com/banco/co/auth/controller/AuthController.java` with `/api/v1/auth/login|refresh|logout`, `@Validated`, and no business logic.
- [x] 3.2 Update security config (`banco-service/src/main/java/com/banco/co/security/config/*`) to expose `AuthenticationManager` bean and wire `CustomUserDetailsService`.
- [x] 3.3 Align exception/handler mapping in `banco-service/src/main/java/com/banco/co/auth/handler/` (or shared handler) for `400/401/403` with safe error payload (no stack traces).

## Phase 4: Migration Consolidation (Legacy Refresh Cleanup)

- [x] 4.1 Create `banco-service/src/main/resources/db/migration/V8__migrate_legacy_refresh_tokens.sql` to backfill legacy refresh data into canonical table.
- [x] 4.2 Mark legacy refresh fields in `banco-service/src/main/java/com/banco/co/user/model/UserCredential.java` as transitional/deprecated and stop runtime writes.
- [x] 4.3 Create `banco-service/src/main/resources/db/migration/V9__drop_legacy_refresh_columns.sql` and remove fallback reads/writes after cutover.

## Phase 5: Tests and Verification

- [x] 5.1 Unit test `AuthService` (`testRefresh_*`, `testLogout_*`, `testReuseDetection_*`) for rotation, invalid/expired token, and global revoke on reuse.
- [x] 5.2 Unit test `CustomUserDetailsService` + `SecurityUser` lock behavior to cover locked vs unlocked scenarios.
- [x] 5.3 `@DataJpaTest` + Testcontainers: verify `IUserCredential` fetch query avoids N+1 and `IRefreshTokenRepository` lock/revoke semantics.
- [x] 5.4 `@WebMvcTest` for auth endpoints: successful login, invalid credentials, locked account, refresh success, refresh reuse attempt, logout success.

## Dependency Order

1) Phase 1 → 2) Phase 2 → 3) Phase 3 → 4) Phase 4 → 5) Phase 5.

Critical dependencies: 2.4 depends on 2.3; 2.5 depends on 1.3–1.5 + 2.2; 3.1 depends on 2.5; 4.3 depends on 4.1 + production cutover validation.

## Remediation (post-verify focused)

- [x] R1 Add explicit unit tests for refresh expired and cryptographically invalid tokens in `AuthServiceTest`.
- [x] R2 Add explicit HTTP contract tests for refresh expired and invalid token responses in `AuthControllerWebMvcTest`.
- [x] R3 Add strict TDD evidence (safety net, red/green, triangulation, refactor) in apply-progress artifact.
- [x] R4 Record Docker/Testcontainers runtime blocker as infrastructure-only and provide rerun command for Docker-enabled verify.
