# Design — hexagonal-arch-phase-3-account

> Status: **DESIGN WITH BLOCKING FINDING**. The proposal's atomic-deletion scope is
> not achievable as written. See **AD-002** and the **Blocking Finding** section.
> Architecture below documents the chosen approach AND the mandatory scope correction.

## Architecture approach

Pattern: Ports & Adapters (hexagonal), feature-first / Screaming Architecture, matching the
`transaction` reference slice. Single inbound port (`IAccountUseCase`) is the only contract for
internal account operations and cross-feature callers. Single outbound port (`IAccountRepository`,
domain `port.out`) backed by `AccountJpaAdapter`. The application service (`AccountService`) becomes
pure orchestration over the domain `Account` POJO and the two ports.

Dependency direction (inward only):
- `adapter.in.rest.AccountController` → `IAccountUseCase`
- `AccountService` (impl of `IAccountUseCase`) → `IAccountRepository` (port.out) + domain `Account`
- `AccountJpaAdapter` (impl of `IAccountRepository`) → `AccountEntity` + Spring Data
- Cross-feature callers (`TransactionService`, `EnvelopeService`, `CardService`) → `IAccountUseCase` + domain `Account`

---

## ⛔ Blocking Finding (must be resolved before/within this slice)

The proposal assumes the legacy `com.banco.co.account.model.Account` `@Entity` can be deleted
atomically with controller activation, and that `card`/`envelope` "remain legacy except for the
caller-injection swap." **That is not true at the JPA level.**

The legacy `Account` `@Entity` is the **JPA owning/inverse partner** of three live relationships:

| Owner class | Field | Mapping | Points at |
|-------------|-------|---------|-----------|
| `user.model.User` | `accounts` | `@OneToMany(mappedBy="user")` | legacy `account.model.Account` |
| `card.model.Card` | `account` | `@ManyToOne @JoinColumn("account_id")` (owning) | legacy `account.model.Account` |
| `envelope.model.Envelope` | `account` | `@ManyToOne @JoinColumn("account_id")` (owning) | legacy `account.model.Account` |

Meanwhile the hexagonal `adapter.out.jpa.AccountEntity` declares
`@OneToMany(mappedBy="account") List<Card>` and `Set<Envelope>` — but those `Card.account` /
`Envelope.account` fields are typed to the **legacy** entity, not `AccountEntity`. So today the live
JPA object graph runs through the **legacy** entity; `AccountEntity`'s `mappedBy` collections are
type-inconsistent and only "work" because the legacy entity is the one Hibernate actually wires.

Consequences:
1. Deleting `account.model.Account` **does not compile** — `Card`, `Envelope`, and `User` reference it.
2. `CardService.createCard` does `card.setAccount(account)` and `TransactionService.payment` does
   `card.getAccount()`. Both bind `Card` to the legacy `Account` type. A use-case that returns the
   domain `Account` POJO cannot satisfy `card.setAccount(...)`.
3. `TransactionService` / `EnvelopeService` / `CardService` read `account.getUser().getEmail()`,
   `account.getUser().getFistName()`, and pass `account.getUser()` (a `User` entity) into audit/
   notification payloads. The domain `Account` has **only `userId`** — no `User`, no email, no name.

This is why `transaction` contracted cleanly and `account` cannot with the same recipe: `Transaction`
holds `fromAccountId`/`toAccountId` UUIDs (no owning JPA FK to Account), whereas `Card`/`Envelope`/`User`
own real FKs into the account table through the legacy type.

### Required scope correction (two viable paths)

- **Path 1 — Re-point JPA owners to `AccountEntity` (true completion).** Change `Card.account`,
  `Envelope.account`, and `User.accounts` to reference `AccountEntity`; update `AccountEntity.mappedBy`
  consistency; migrate the three caller services to consume domain `Account` via `IAccountUseCase`
  and resolve user identity (email/name) through `IUserService`/`IUserRepository` instead of
  `account.getUser()`. THEN delete legacy `Account.java`. This is the only path that ends with one
  `@Entity` on `"account"`. It necessarily pulls `Card`/`Envelope`/`User` entity edits INTO this slice
  — contradicting the proposal's "card/envelope remain legacy" boundary. **Recommended**, but the
  proposal scope and review-workload budget must be updated to admit it.

- **Path 2 — Defer legacy deletion; complete only the service + inbound activation.** Rewrite
  `AccountService` onto the domain port, activate the hexagonal controllers, and migrate the callers
  that can move (those not bound to `card.setAccount`). KEEP legacy `Account.java` because Card/
  Envelope/User still own it. This does NOT remove the dual-`@Entity` hazard and therefore does NOT
  meet the proposal's success criteria ("exactly one `@Entity` mapped to account"). Lower blast radius,
  but it is a partial slice and must be labelled as such.

**Design recommendation:** adopt **Path 1** and formally expand the slice to include `Card`,
`Envelope`, and `User` entity re-pointing, OR split into two chained changes
(`...-account-service` then `...-account-legacy-removal`). The atomic-deletion constraint (AD-002)
is correct in spirit but its file list in the proposal is incomplete: it omits the Card/Envelope/User
edits that deletion forces.

The decisions below are written for **Path 1** (the architecturally correct completion). If the
orchestrator/user chooses Path 2, AD-002 and the file inventory shrink accordingly and the change is
marked `partial`.

---

## Architecture Decisions (ADR-style)

### AD-001 — `IAccountUseCase` as the single unified inbound contract

**Decision.** Extend `IAccountUseCase` with the 5 internal methods, typed to the **domain** `Account`,
and make it the sole inbound interface for every caller. Delete `IAccountService`.

| Method | Signature (domain-typed) |
|--------|--------------------------|
| `getAccountById` | `Account getAccountById(UUID accountId)` |
| `findAccountWithUserByAccountCode` | `Account findAccountWithUserByAccountCode(String accountCode)` |
| `updateBalance` | `void updateBalance(Account account)` |
| `validateCanReceiveDeposit` | `void validateCanReceiveDeposit(Account account)` |
| `validateCanWithdraw` | `void validateCanWithdraw(Account account, BigDecimal amount)` |

**Rationale.** The domain `Account` already carries every business method (`blockFunds`,
`confirmBlockedFunds`, `deposit`, `withdraw`, `unblockFunds`). A separate "internal port" (Approach B
facade) or a dedicated query port (Approach C) would leave two interfaces and two return types behind —
exactly the dual-surface smell this contraction is meant to remove. One port, domain-typed, is the
end state the `transaction` reference already proved.

**Rejected alternatives.**
- *Keep `IAccountService` as a legacy facade* — leaves dual interfaces; callers stay ambiguous about
  which contract is canonical. Rejected.
- *Introduce `IAccountQueryPort`* — over-engineering for five methods on a single aggregate. Rejected.

**Tradeoff.** Putting persistence-flavored helpers (`updateBalance`) on the inbound use-case port is a
mild purity compromise: a "use case" exposing a save-shaped method. Accepted because callers
orchestrate domain mutations and need an explicit persist step, and because the alternative (exposing
the outbound port to callers) breaks the boundary far worse.

---

### AD-002 — Atomic deletion constraint (corrected)

**Decision.** Legacy `@Entity` deletion and hexagonal inbound activation MUST land in **one commit**,
AND that commit MUST also re-point the JPA owners (`Card`, `Envelope`, `User`) to `AccountEntity`.
There is no compiling, bootable intermediate state between "legacy `Account` exists" and "legacy
`Account` deleted + owners re-pointed."

**Dual-`@Entity` boot hazard.** Two `@Entity` classes (`account.model.Account` and
`adapter.out.jpa.AccountEntity`) map `@Table(name="account")`. Hibernate must resolve exactly one
persistent class per table; with both active, mapping is non-deterministic / context startup is at
risk. The only safe sequence is: make `AccountEntity` the sole owner of every relationship into the
account table, then delete the legacy class — in the same commit.

**Why it cannot be smaller.** Because `Card.account` / `Envelope.account` (owning FK side) and
`User.accounts` (inverse side) are typed to the legacy class, the compiler forces all four edits
(delete + 3 re-points) to be simultaneous. This is the hard floor on commit size.

**Exact files in the atomic commit (Path 1):**
1. DELETE `account/model/Account.java`
2. DELETE `account/repository/IAccountRepository.java` (legacy Spring Data)
3. DELETE `account/repository/IAccountMapper.java` (misnamed dead `JpaRepository`)
4. DELETE `account/mapper/IAccountMapper.java` (legacy MapStruct mapper)
5. DELETE `account/service/IAccountService.java`
6. DELETE `account/controller/AccountController.java` (legacy)
7. DELETE `account/controller/AccountAdminController.java` (legacy)
8. MODIFY `account/adapter/in/rest/AccountController.java` — uncomment `@RestController` + `@RequestMapping`
9. MODIFY `account/adapter/in/rest/AccountAdminController.java` — same activation
10. MODIFY `card/model/Card.java` — `account` field → `AccountEntity`
11. MODIFY `envelope/model/Envelope.java` — `account` field → `AccountEntity`
12. MODIFY `user/model/User.java` — `accounts` field → `List<AccountEntity>`

Steps 1–9 alone do not compile without 10–12; that is the proposal's omission. Boot verification
(single `@Entity` on `"account"`, context starts) is the merge gate.

---

### AD-003 — Cross-feature caller migration strategy

**Decision.** `TransactionService`, `EnvelopeService`, `CardService` inject `IAccountUseCase` and
operate on the domain `Account`. Ownership checks switch from
`account.getUser().getId()` → `account.getUserId()`.

**Why this is safe for security-critical ownership in `TransactionService`.** The ownership check
compares the account's owner id against the authenticated user's id. The domain `Account.getUserId()`
is populated by `AccountEntityMapper.toDomain()` from `AccountEntity.user.id` — the same FK the legacy
`account.getUser().getId()` reads. The compared value is byte-identical; only the navigation path
changes (UUID field vs. lazy `@ManyToOne` traversal). Switching also removes a lazy-load that could
otherwise throw outside a session, making the check *more* robust. Characterization/authorization
tests (transfer-from-unowned-account, service-payment-from-unowned-account) must be green BEFORE the
legacy entity is deleted.

**The identity-data gap (must be handled, not hand-waved).** Three call sites need user data the
domain `Account` does NOT carry:
- `TransactionService.buildAccountNode` → `account.getUser().getId()/getEmail()/getFistName()`
- `EnvelopeScheduleService` (lines 128, 185) → passes `account.getUser()` (a `User` entity)
- `CardService` audit logs → `account.getUser().getEmail()`

Migration rule: replace `account.getUser().getX()` with a lookup through `IUserService` /
`IUserRepository` keyed by `account.getUserId()`. For notification payloads, fetch a `UserSnapshot`
(already used in `AccountService.createAccount`) and read id/email/name from it. No call site may
assume the domain `Account` exposes a `User`.

**The `card.setAccount` / `card.getAccount` constraint.** `CardService.createCard` does
`card.setAccount(account)` and `TransactionService.payment` reads `card.getAccount()`. After Path 1,
`Card.account` is an `AccountEntity`. Two sub-options:
- *(preferred)* `CardService` keeps an `AccountEntity` reference only at the persistence boundary:
  obtain the domain `Account` via `IAccountUseCase` for validation, and obtain the `AccountEntity`
  for the FK via the account JPA layer / a dedicated port method. To avoid leaking `AccountEntity`
  into `CardService`, expose a use-case method that links a card to an account by id, OR have the
  card adapter resolve the `AccountEntity` from `account_id`. The cleanest fit is for the **card
  adapter** to resolve the `AccountEntity` from the domain account's id at save time (mirroring how
  `AccountJpaAdapter.save` resolves `User` from `userId`).
- `TransactionService.payment` replaces `card.getAccount()` with
  `accountService.getAccountById(card.getAccountId())` (domain `Account`), if `Card` exposes
  `accountId`. If it does not, add it. This removes the entity-graph hop entirely.

This sub-design is the riskiest part of the slice and is the main reason the change is larger than the
proposal estimated. The `sdd-tasks` phase must enumerate every `card.getAccount()` / `card.setAccount`
call site.

---

### AD-004 — Test rewrite strategy

**Safety net (KEEP, unchanged).**
- `account/domain/model/AccountTest.java` — pure domain rules on the domain `Account`. This is the
  behavioral spec for `blockFunds`/`confirmBlockedFunds`/`deposit`/`withdraw`/validators. It already
  targets the correct type and must stay green throughout.
- `account/adapter/out/jpa/AccountJpaAdapterIntegrationTest.java` — proves the adapter path
  (domain ↔ `AccountEntity` ↔ DB) works. After the service rewrite, this is what guarantees
  `AccountService` actually persists through `AccountJpaAdapter`. It is the characterization net for
  the persistence boundary.

**REWRITE (mock `IAccountUseCase`, target hexagonal controllers).**
- `account/controller/AccountControllerTest.java` → target `adapter/in/rest/AccountController`,
  mock `IAccountUseCase`.
- `account/controller/AccountAdminControllerTest.java` → target hexagonal admin controller.
- `account/controller/AccountControllerSecuritySliceWebMvcTest.java` → hexagonal controller security slice.
- `card/service/CardServiceTest.java` → mock `IAccountUseCase` instead of `IAccountService`; adjust
  for domain `Account` return + the `card.setAccount` resolution chosen in AD-003.
- `transaction/service/TransactionServiceNotificationTest.java` and
  `TransactionServiceFraudGateTest.java` → mock `IAccountUseCase`; stub `IUserService`/snapshot for
  the identity data now sourced outside `Account`.
- `security/controller/ControllerSecurityAnnotationsTest.java` → update references to hexagonal controllers.

**DELETE.**
- `account/model/AccountLifecycleTest.java` (a.k.a. `AccountTest` in `account/model`) — tests the dead
  legacy entity's `@PrePersist` lifecycle. Gone with the entity.

**Rationale.** Domain + adapter integration tests are type-correct and behavior-anchored, so they are
the regression backstop while everything around them is rewritten. Controller/service tests are
coupled to the deleted `IAccountService` type and must be re-pointed, not patched.

---

## File change inventory

> Path 1 (recommended). Paths are relative to
> `banco-service/src/main/java/com/banco/co` and `.../test/java/com/banco/co`.

| File | Action | What changes |
|------|--------|--------------|
| `account/domain/port/in/IAccountUseCase.java` | MODIFY | Add 5 internal methods, domain-`Account`-typed |
| `account/service/AccountService.java` | REWRITE | Inject `IAccountRepository` (port.out) only; implement extended `IAccountUseCase`; drop legacy repo/mapper; port `toJsonString` via `ObjectMapper`; use domain `Account` throughout |
| `account/service/IAccountService.java` | DELETE | Legacy contract removed |
| `account/repository/IAccountRepository.java` | DELETE | Legacy Spring Data repo |
| `account/repository/IAccountMapper.java` | DELETE | Misnamed dead `JpaRepository` |
| `account/mapper/IAccountMapper.java` | DELETE | Legacy MapStruct mapper (port `toJsonString` first) |
| `account/model/Account.java` | DELETE | Legacy `@Entity` (atomic with re-points) |
| `account/controller/AccountController.java` | DELETE | Legacy REST controller |
| `account/controller/AccountAdminController.java` | DELETE | Legacy admin REST controller |
| `account/adapter/in/rest/AccountController.java` | MODIFY | Uncomment `@RestController` + `@RequestMapping("/api/v1/accounts")` |
| `account/adapter/in/rest/AccountAdminController.java` | MODIFY | Activate annotations |
| `card/model/Card.java` | MODIFY | `account` `@ManyToOne` → `AccountEntity`; expose/keep `accountId` if needed |
| `envelope/model/Envelope.java` | MODIFY | `account` `@ManyToOne` → `AccountEntity` |
| `user/model/User.java` | MODIFY | `accounts` `@OneToMany` → `List<AccountEntity>` |
| `transaction/service/TransactionService.java` | MODIFY | Inject `IAccountUseCase`; domain `Account`; `getUserId()`; source user email/name via `IUserService`/snapshot; `payment` drops `card.getAccount()` entity hop |
| `envelope/service/EnvelopeService.java` | MODIFY | Inject `IAccountUseCase`; ownership via `account.getUserId()` |
| `envelope/service/EnvelopeScheduleService.java` | MODIFY | Replace `account.getUser()` usage with snapshot/user lookup by `userId` |
| `card/service/CardService.java` | MODIFY | Inject `IAccountUseCase`; ownership via `getUserId()`; resolve `AccountEntity` for `card` FK at adapter boundary; audit email via user lookup |
| `account/domain/port/out/IAccountRepository.java` | MODIFY (maybe) | Add method to resolve/link `AccountEntity` for card FK if AD-003 needs it |
| `account/controller/AccountControllerTest.java` | REWRITE | Target hexagonal controller, mock `IAccountUseCase` |
| `account/controller/AccountAdminControllerTest.java` | REWRITE | Target hexagonal admin controller |
| `account/controller/AccountControllerSecuritySliceWebMvcTest.java` | REWRITE | Hexagonal controller security slice |
| `card/service/CardServiceTest.java` | REWRITE | Mock `IAccountUseCase`; domain `Account` |
| `transaction/service/TransactionServiceNotificationTest.java` | REWRITE | Mock `IAccountUseCase` + user snapshot |
| `transaction/service/TransactionServiceFraudGateTest.java` | REWRITE | Mock `IAccountUseCase` |
| `security/controller/ControllerSecurityAnnotationsTest.java` | MODIFY | Reference hexagonal controllers |
| `account/model/AccountLifecycleTest.java` | DELETE | Tests dead legacy entity |
| `account/domain/model/AccountTest.java` | KEEP | Domain safety net |
| `account/adapter/out/jpa/AccountJpaAdapterIntegrationTest.java` | KEEP | Adapter/persistence safety net |

---

## Dependency / implementation order (avoid compile breaks)

1. **Extend `IAccountUseCase`** with the 5 domain-typed methods. (Pure addition; compiles because
   `AccountService` already declares the same methods via `IAccountService` — but their types differ,
   so `AccountService` must implement the new signatures before `IAccountService` is removed. Do step 2
   immediately after.)
2. **Rewrite `AccountService`** to inject only `IAccountRepository` (port.out) + `IUserRepository`/
   `IUserService` for snapshots; implement the extended `IAccountUseCase`; port `toJsonString`. At
   this point `AccountService` no longer implements `IAccountService`.
3. **Migrate cross-feature callers** to `IAccountUseCase` + domain `Account`
   (`TransactionService`, `EnvelopeService`, `EnvelopeScheduleService`, `CardService`), resolving the
   identity-data gap and the `card.setAccount` constraint. After this, nothing references
   `IAccountService` or legacy `Account`.
4. **Atomic commit (AD-002):** re-point `Card`/`Envelope`/`User` entities to `AccountEntity`, activate
   hexagonal controllers, delete the entire legacy stack. Boot verification gate.
5. **Rewrite/delete tests** per AD-004; full suite green.

Steps 1–3 are individually compilable only if done in the order shown; step 4 is indivisible.

---

## TDD approach per task (RED test first)

- **Extend `IAccountUseCase` / rewrite `AccountService`** — RED: a use-case unit test asserting
  `getAccountById` returns a domain `Account` whose `userId` equals the persisted account's owner id,
  with `IAccountRepository` faked. Also RED: `updateBalance` persists via the outbound port (verify
  port `save` called). Keep `AccountJpaAdapterIntegrationTest` green as the real-DB anchor.
- **Ownership migration in callers** — RED (security): transfer / service-payment / card-create /
  envelope-op from an account owned by another user throws `UnauthorizedException`, asserted via
  `account.getUserId()` mismatch (mock `IAccountUseCase` returning a domain `Account` with a foreign
  `userId`). These MUST exist and pass before legacy deletion.
- **Identity-data gap** — RED: notification payload test asserting `buildAccountNode` produces the
  correct `userId/email/firstName` sourced from the user snapshot (not from `account.getUser()`).
- **`card.setAccount` resolution** — RED: `CardServiceTest.createCard` persists a card whose
  `account_id` FK equals the validated account's id, with `IAccountUseCase` returning a domain
  `Account` and the card adapter resolving the `AccountEntity`.
- **Inbound activation** — RED: `@WebMvcTest` against `adapter/in/rest/AccountController` asserting the
  legacy URL contract (`GET /api/v1/accounts`, `/{id}`, `/code/{code}`, `/{id}/balance`, `POST`,
  `PUT /{code}`, `DELETE /{id}`) and `@PreAuthorize` rules are preserved byte-for-byte.
- **Boot gate** — RED→GREEN: a Spring context / Testcontainers boot test asserting exactly one
  persistent class maps `"account"` and the context starts.

---

## Risks (architectural)

1. **CRITICAL — scope contradiction.** Proposal says card/envelope stay legacy, but JPA FK ownership
   forces their entity edits into the deletion commit. Must be reconciled (Path 1 expand-scope, or
   Path 2 defer-deletion + mark partial) BEFORE `sdd-tasks`.
2. **HIGH — identity-data gap.** Domain `Account` lacks `User`/email/name; every `account.getUser()`
   call site needs a user lookup. Missed sites = NPE / wrong audit/notification data.
3. **HIGH — `card.setAccount`/`card.getAccount` entity coupling.** `Card` owns an FK to the account
   entity; the use-case returns a POJO. Needs an adapter-side `AccountEntity` resolution or a
   `Card.accountId` path. Riskiest mechanical change.
4. **MEDIUM — review-workload floor.** Atomic commit now spans account + card + envelope + user +
   tests; comfortably exceeds a small changed-line budget. Delivery shape (single PR with
   `size:exception`, or two chained changes) is a `sdd-tasks` decision but is effectively forced.
5. **MEDIUM — URL contract drift.** Hexagonal controllers must preserve legacy mappings exactly;
   verify before activation (contract change is out of scope).
6. **LOW — audit JSON parity.** `toJsonString` ported to `ObjectMapper` must keep audit output equivalent.
```
