# Exploration — hexagonal-arch-phase-3-account

## Status

`done`

## Executive Summary

The account hexagonal skeleton is ~80% complete — domain model, ports, JPA adapter, and REST controllers are written and structurally correct — but migration is blocked by three concrete gaps:

1. `AccountService` still injects the **legacy** `IAccountRepository`, not the domain port.
2. `IAccountUseCase` is missing **5 internal methods** needed by cross-feature callers.
3. **Three external services** (`TransactionService`, `EnvelopeService`, `CardService`) inject `IAccountService` and return the legacy `Account` entity.

---

## What the Hexagonal Skeleton Has (Complete)

| File | Status |
|---|---|
| `account/domain/model/Account.java` | ✅ Pure POJO, zero JPA/Spring, all business methods identical to legacy |
| `account/domain/port/in/IAccountUseCase.java` | ✅ 9 user-facing methods — missing 5 internal |
| `account/domain/port/out/IAccountRepository.java` | ✅ 10 persistence methods, zero JPA imports |
| `account/adapter/out/jpa/AccountEntity.java` | ✅ Anemic JPA entity on `"account"` table |
| `account/adapter/out/jpa/AccountJpaAdapter.java` | ✅ Fully implements `IAccountRepository` |
| `account/adapter/out/jpa/IAccountJpaRepository.java` | ✅ All required Spring Data queries |
| `account/adapter/out/jpa/AccountEntityMapper.java` | ✅ MapStruct mapper, handles cross-feature UUID extraction |
| `account/adapter/in/rest/AccountController.java` | ✅ Complete, RBAC present — `@RestController` commented out |
| `account/adapter/in/rest/AccountAdminController.java` | ✅ Complete — `@RestController` commented out |

---

## Gaps

### Gap 1 — AccountService does not wire to the domain port
`AccountService` injects `com.banco.co.account.repository.IAccountRepository` (legacy), not the domain output port. `AccountJpaAdapter` is Spring-managed but never called through the service.

### Gap 2 — 5 internal methods missing from IAccountUseCase
Explicitly noted in the interface source. These were deferred during the additive phase to avoid conflicting return types:
- `getAccountById`
- `findAccountWithUserByAccountCode`
- `updateBalance`
- `validateCanReceiveDeposit`
- `validateCanWithdraw`

### Gap 3 — Three external callers depend on legacy IAccountService

| Consumer | Methods used | Legacy Account usage |
|---|---|---|
| `TransactionService` | `findAccountWithUserByAccountCode`, `validateCanWithdraw`, `validateCanReceiveDeposit`, `updateBalance` | `account.getUser().getId()` → migrate to `account.getUserId()` |
| `EnvelopeService` | `findAccountWithUserByAccountCode` | `account.getUser().getId()` → migrate to `account.getUserId()` |
| `CardService` | unknown (partial read) | Imports legacy `Account` |
| `TransactionJpaAdapter` | FK resolution only | Already on hexagonal path — no change needed |

### Gap 4 — Dual @Entity conflict (CRITICAL)
`account/model/Account.java` and `account/adapter/out/jpa/AccountEntity.java` both map to `"account"` table. Will throw at boot. Legacy deletion must happen in the same commit that activates the hexagonal controllers.

### Gap 5 — Tests targeting legacy types
- `AccountControllerTest`, `AccountAdminControllerTest`, `AccountControllerSecuritySliceWebMvcTest` — mock `IAccountService`, target legacy controller. Must be rewritten.
- `AccountLifecycleTest` (`account/model/`) — tests legacy entity. Delete.
- Keep: `AccountTest` (domain model), `AccountJpaAdapterIntegrationTest`.

---

## Approach Recommendation: A

Extend `IAccountUseCase` with the 5 internal methods (domain-typed), rewrite `AccountService` to use the domain port, update cross-feature callers, activate adapter controllers, delete legacy.

---

## Risks

| Priority | Risk |
|---|---|
| CRITICAL | Dual `@Entity` on same table — cannot partially activate |
| HIGH | `TransactionService` uses `account.getUser().getId()` for security ownership checks → must swap to `account.getUserId()` |
| MEDIUM | `AccountService` never calls `AccountJpaAdapter` today |
| MEDIUM | 3 test classes must be rewritten |
| LOW | `account/repository/IAccountMapper.java` — dead code, delete |
| LOW | `account/mapper/IAccountMapper.java#toJsonString()` used for audit logs — replace with `ObjectMapper` |
