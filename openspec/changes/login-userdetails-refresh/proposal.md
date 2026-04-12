# Proposal: Login, UserDetailsService, and Stateful Refresh Token

## Intent
Implement a robust, stateful authentication flow (Login + Refresh + Logout) to enable secure API access. We will address critical security bugs in the current base (lock-check inversion), consolidate refresh token source-of-truth in the DB, and add the missing UserDetailsService implementation to connect with Spring Security's AuthenticationManager.

## Scope

### In Scope
- `UserDetailsService` implementation fetching user and roles (preventing N+1).
- `/api/v1/auth/login` endpoint returning Access and Refresh JWTs.
- `/api/v1/auth/refresh` endpoint with stateful token rotation and reuse detection.
- `/api/v1/auth/logout` endpoint for explicit token revocation.
- Fix inverted logic in `SecurityUser.isAccountNonLocked()`.
- Add `@EntityListeners(AuditingEntityListener.class)` to `RefreshToken`.
- Flyway migration for the `refresh_tokens` table.
- Consolidate refresh token logic to use only the `RefreshToken` entity (removing legacy fields from `UserCredential`).

### Out of Scope
- OAuth2 / Social Login integration.
- Multi-factor authentication (MFA).
- IP-based or device-fingerprinting security heuristics.

## Capabilities

### New Capabilities
- `user-auth`: Covers Login, Refresh Token rotation, and Logout operations.

### Modified Capabilities
- None

## Approach
Implement a **Stateful refresh robusto con rotación y reuse detection**.
We will define the REST controllers for auth, backed by a service that interacts with `AuthenticationManager`. Upon successful login, we generate an access token and a refresh token. The refresh token will be stored in the new `refresh_tokens` table. On refresh, we rotate the token (issue a new one, revoke the old one). If a revoked token is used, we treat it as a reuse attempt and revoke the entire token family. We will also clean up redundant fields in `UserCredential` and fix the `SecurityUser` lock check bug.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `com.banco.co.security.config` | Modified | Add `AuthenticationManager` bean, wire UserDetailsService |
| `com.banco.co.user.model` | Modified | Fix `SecurityUser` lock logic, remove legacy refresh fields |
| `com.banco.co.security.token` | Modified | Add Auditing to `RefreshToken`, implement rotation/revocation |
| `com.banco.co.auth` | New | `AuthController`, `AuthService`, DTOs |
| `db/migration` | New | Flyway script for `refresh_tokens` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| N+1 queries during UserDetailsService load | High | Use `JOIN FETCH` for user roles/authorities in the repository. |
| Incomplete auditing on RefreshToken | Medium | Add `@EntityListeners(AuditingEntityListener.class)` and ensure JPA Auditing is enabled. |
| Transactional issues on token reuse | Medium | Ensure token revocation runs in a required transaction to prevent race conditions. |

## Rollback Plan
- Revert the Flyway migration (down script).
- Revert the `AuthController` and `AuthService` additions.
- Restore the legacy `refreshToken` fields in `UserCredential` if necessary.

## Dependencies
- Existing `JwtUtils` for token generation/validation.
- Spring Security `AuthenticationManager`.

## Success Criteria
- [ ] Users can successfully login and receive valid Access and Refresh tokens.
- [ ] Refreshing a token issues a new pair and revokes the old Refresh token in the DB.
- [ ] Attempting to use a revoked refresh token revokes all associated tokens for that user (reuse detection).
- [ ] Logout successfully revokes the provided refresh token.
- [ ] Locked accounts are properly denied access (lock bug fixed).
