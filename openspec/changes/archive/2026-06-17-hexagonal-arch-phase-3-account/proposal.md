# Proposal — hexagonal-arch-phase-3-account

## Intent

Complete the hexagonal / ports-and-adapters migration of the `account` feature by removing the legacy flat stack that currently co-exists with the (already ~80% complete) hexagonal skeleton. This is the **contraction phase**: the additive phase produced a full hexagonal skeleton, but `AccountService` still runs on legacy infrastructure and three cross-feature callers still couple to the legacy `Account` `@Entity`. This change closes the remaining gaps, activates the hexagonal REST adapters, and deletes the legacy stack in one coherent sweep so the `account` feature matches the `transaction` reference implementation.

**Why now**: Two `@Entity` classes (`account/model/Account.java` and `account/adapter/out/jpa/AccountEntity.java`) map to the SAME `"account"` table. This dual mapping is a latent runtime hazard — only one can be active. The skeleton is finished but unused (`AccountJpaAdapter` exists yet `AccountService` injects the legacy repository). Leaving the feature half-migrated carries more risk than completing it: every future account change must reason about two parallel stacks.

**Success looks like**:
- `AccountService` injects the domain output port `IAccountRepository` (and thus uses `AccountJpaAdapter`), never the legacy Spring Data repository.
- `IAccountUseCase` is the single inbound contract for ALL callers — internal and cross-feature.
- `TransactionService`, `EnvelopeService`, and `CardService` depend on `IAccountUseCase` and the domain `Account` model (UUID-based cross-feature refs), not the legacy `Account` `@Entity`.
- The hexagonal REST controllers are active; the legacy controllers, service, repository, mapper, model, and legacy tests are deleted.
- The application boots with exactly one `@Entity` mapped to `"account"`.
- Test suite is green, rewritten against hexagonal types.

## Scope

### In

1. **Extend `IAccountUseCase`** with the 5 internal methods, typed to the **domain** `Account` (not legacy):
   - `getAccountById`
   - `findAccountWithUserByAccountCode`
   - `updateBalance`
   - `validateCanReceiveDeposit`
   - `validateCanWithdraw`
2. **Rewrite `AccountService`** to inject the domain output port `IAccountRepository` (backed by `AccountJpaAdapter`) instead of the legacy Spring Data `IAccountRepository`. Port `toJsonString` audit-log behavior to the new service (use `ObjectMapper` directly or a domain-side helper).
3. **Migrate cross-feature callers** to inject `IAccountUseCase` and operate on the domain `Account`:
   - `TransactionService` — replace `account.getUser().getId()` with `account.getUserId()`; call domain `Account` methods (`blockFunds`, `confirmBlockedFunds`, `deposit`, validators) via the use case.
   - `EnvelopeService` — replace ownership check via `account.getUser().getId()` with `account.getUserId()`.
   - `CardService` — swap `IAccountService` dependency for `IAccountUseCase`.
4. **Activate hexagonal REST adapters** — uncomment `@RestController` / `@RequestMapping` on `account/adapter/in/rest/AccountController.java` and `AccountAdminController.java`.
5. **Delete the legacy stack ATOMICALLY** in the same commit that activates the hexagonal controllers:
   - `account/model/Account.java` (legacy `@Entity`)
   - `account/repository/IAccountRepository.java` (legacy Spring Data)
   - `account/repository/IAccountMapper.java` (misnamed dead `JpaRepository`)
   - `account/mapper/IAccountMapper.java` (legacy MapStruct mapper)
   - `account/service/IAccountService.java`
   - `account/controller/AccountController.java` + `AccountAdminController.java` (legacy)
6. **Rewrite tests** against hexagonal types:
   - `AccountControllerTest`, `AccountAdminControllerTest`, `AccountControllerSecuritySliceWebMvcTest` → mock `IAccountUseCase`, target hexagonal controllers.
   - Delete `AccountLifecycleTest` (account/model) — tests dead legacy entity.
   - Keep `AccountTest` (account/domain/model) and `AccountJpaAdapterIntegrationTest` — already correct.
   - Update `TransactionService` tests that mock `IAccountService` → mock `IAccountUseCase`.

### Out

- Full hexagonal migration of other features (`user` is done; `card`, `envelope` remain legacy except for the caller-injection swap required here).
- Endpoint contract / URL changes — the hexagonal controllers preserve the legacy mappings.
- DTO shape changes.
- Flyway / schema migration — both `@Entity` classes already share the `"account"` table.
- Introduction of a separate `IAccountQueryPort` (Approach C) — rejected as over-engineering for this slice.
- Keeping a legacy `IAccountService` facade (Approach B) — rejected; it leaves dual interfaces behind.

## Approach chosen — A: `IAccountUseCase` extension + service rewrite + caller migration

Selected over B (facade) and C (dedicated query port). Approach A is a single contraction sweep that leaves no facades, no dual interfaces, and no extra ports. The hexagonal skeleton already carries every business method on the domain `Account`, so callers can operate directly on the domain model once they depend on `IAccountUseCase`.

## Key decisions

1. **Single inbound contract**: `IAccountUseCase` is the one interface for both internal account operations and cross-feature callers. No separate query port. The 5 internal methods are added to it, typed to the domain `Account`.
2. **UUID cross-feature refs**: callers replace `account.getUser().getId()` with the domain model's `account.getUserId()`. This removes coupling to the legacy `Account` `@Entity`'s `@ManyToOne User` relationship for security-critical ownership checks.
3. **Atomic deletion + activation**: legacy stack deletion and hexagonal controller activation MUST land in the same commit. Two `@Entity` classes mapping to `"account"` cannot both be active — the boot would fail or behave non-deterministically. There is no safe intermediate state where both exist active.
4. **Tests rewritten, not adapted**: legacy-entity tests are deleted; controller/service tests are rewritten against hexagonal types. The already-correct domain and adapter integration tests are preserved as the safety net.
5. **Audit-log JSON behavior preserved**: the legacy mapper's `toJsonString(Account)` (used in audit logs) is ported to the rewritten service via `ObjectMapper` so audit output stays equivalent.

## Risk register

| # | Severity | Risk | Mitigation |
|---|----------|------|------------|
| 1 | CRITICAL | Two `@Entity` classes map to the `"account"` table; both active = Spring context failure / non-deterministic mapping. | Delete legacy `Account.java` in the SAME commit that activates the hexagonal controllers. Boot verification is a gate. |
| 2 | HIGH | `TransactionService` and `EnvelopeService` use the legacy `Account` entity for security-critical ownership (`getUser().getId()`). | Migrate callers ATOMICALLY with the service rewrite; switch to `account.getUserId()`. Cover with ownership/authorization tests before deletion. |
| 3 | MEDIUM | `AccountService` currently bypasses `AccountJpaAdapter` (injects legacy repo). | Rewrite service to inject domain `IAccountRepository`; integration test confirms the adapter path is exercised. |
| 4 | MEDIUM | 3+ test files mock `IAccountService` and will break. | Rewrite to mock `IAccountUseCase`; keep domain + adapter integration tests as characterization safety net. |
| 5 | LOW | `account/repository/IAccountMapper.java` is a misnamed `JpaRepository` (dead code). | Delete with the legacy stack. |
| 6 | LOW | Legacy mapper's `toJsonString()` is used in audit logs. | Port behavior to the rewritten service (ObjectMapper) so audit output is unchanged. |

## Open questions / assumptions needing review

- **Assumption**: the hexagonal `AccountController` / `AccountAdminController` URL mappings are byte-for-byte equivalent to the legacy controllers (exploration confirms identical mappings). If any path/verb differs, it must be reconciled before activation — contract change is OUT of scope.
- **Assumption**: `CardService`'s use of `IAccountService` is limited to operations covered by `IAccountUseCase`'s surface (exploration confirmed the injection but not every call site). Spec phase must enumerate `CardService`'s exact account calls.
- **Decision gap**: whether the migration ships as a single PR or chained (legacy-delete is inherently atomic with controller activation, so the contraction cannot be split below that boundary). Delivery shape is a `sdd-tasks` concern, flagged here for awareness.

## Review workload note

This is a contraction slice with deletions + a service rewrite + multi-feature caller updates + test rewrites. It is likely to exceed a small changed-line budget. The atomic-deletion constraint (decision #3) sets a hard floor on how small the final commit can be. Delivery strategy / size exception is deferred to `sdd-tasks`.
