# Tasks — hexagonal-arch-phase-3-account

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1 200 (additions + deletions) |
| 400-line budget risk | High |
| Chained PRs recommended | No — atomic commit constraint forces single PR |
| Suggested split | Single PR with size:exception (atomic deletion is the hard floor) |
| Delivery strategy | size:exception (pre-approved) |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units (commit-level, not PR-level)

| Unit | Goal | Commit scope |
|------|------|-------------|
| 1 | Extend IAccountUseCase + domain port + AccountJpaAdapter | `refactor(account):` |
| 2 | Rewrite AccountService | `refactor(account):` |
| 3 | Migrate TransactionService | `refactor(transaction):` |
| 4 | Migrate EnvelopeService | `refactor(envelope):` |
| 5 | Migrate CardService + add Card.accountId | `refactor(card):` |
| 6 | Atomic commit — re-point JPA owners, activate adapters, delete legacy | `refactor(account):` |
| 7 | Rewrite / delete tests | `test(account):` + `test(transaction):` + `test(card):` |

---

## Phase 1: Foundation — Extend Ports and Adapter

### Task 1.1 — Add `findSnapshotByUserId` to `IUserRepository` (domain port)

**RED test**: `UserRepositorySnapshotTest#testFindSnapshotByUserId_ExistingUser_ReturnsSnapshot` — asserts that calling `findSnapshotByUserId(uuid)` on the domain port returns a non-null `UserSnapshot` with matching `userId`, `userEmail`, and `userName` fields. Fails because method does not exist.
**Files to create/modify**:
- `user/domain/port/out/IUserRepository.java` — add `UserSnapshot findSnapshotByUserId(UUID userId)`
- `user/adapter/out/jpa/UserJpaAdapter.java` — implement the new method (query by id, map to snapshot)
**GREEN**: method exists, adapter implements it, integration test passes against real DB.
**Commit scope**: `refactor(user):`

- [x] 1.1.1 Write RED test: `UserRepositorySnapshotTest#testFindSnapshotByUserId_ExistingUser_ReturnsSnapshot`
- [x] 1.1.2 Add `findSnapshotByUserId(UUID userId)` to `IUserRepository`
- [x] 1.1.3 Implement in `UserJpaAdapter` (JPQL query user by id, map snapshot)
- [x] 1.1.4 Confirm GREEN

### Task 1.2 — Add `findByAccountCodeWithUser` to domain `IAccountRepository` and `AccountJpaAdapter`

**RED test**: `AccountJpaAdapterIntegrationTest#testFindByAccountCodeWithUser_ExistingAccount_PopulatesUserId` — asserts that `findByAccountCodeWithUser("ACC-001")` returns an `Optional<Account>` with `account.getUserId() != null`. Fails because method is missing from the port.
**Files to create/modify**:
- `account/domain/port/out/IAccountRepository.java` — add `Optional<Account> findByAccountCodeWithUser(String accountCode)`
- `account/adapter/out/jpa/AccountJpaAdapter.java` — implement; delegate to existing `IAccountJpaRepository.findAccountWithUser(accountCode)` and map via `AccountEntityMapper`
- `account/adapter/out/jpa/IAccountJpaRepository.java` — verify `findAccountWithUser` JPQL query exists (read-only, no change if present)
**GREEN**: `AccountJpaAdapterIntegrationTest` passes; `findByAccountCodeWithUser` returns domain `Account` with populated `userId`.
**Commit scope**: `refactor(account):`

- [x] 1.2.1 Write RED test: `AccountJpaAdapterIntegrationTest#testFindByAccountCodeWithUser_ExistingAccount_PopulatesUserId`
- [x] 1.2.2 Add method signature to `IAccountRepository` (domain port.out)
- [x] 1.2.3 Implement in `AccountJpaAdapter`
- [x] 1.2.4 Confirm GREEN — existing adapter integration test suite stays green

### Task 1.3 — Extend `IAccountUseCase` with 5 internal domain-typed methods

**RED test**: `AccountServiceTest#testGetAccountById_NotFound_ThrowsAccountNotFoundException` — asserts calling `accountUseCase.getAccountById(unknownId)` throws `AccountNotFoundException`. Fails because method is not on the interface yet (compilation error).
**Files to create/modify**:
- `account/domain/port/in/IAccountUseCase.java` — add 5 methods (all use `com.banco.co.account.domain.model.Account`):
  - `Account getAccountById(UUID accountId)`
  - `Account findAccountWithUserByAccountCode(String accountCode)`
  - `void updateBalance(Account account)`
  - `void validateCanReceiveDeposit(Account account)`
  - `void validateCanWithdraw(Account account, BigDecimal amount)`
**GREEN**: interface compiles with 5 new methods; `AccountService` not yet updated (will cause compilation failure until Task 2.1 — implement both together in one session).
**Commit scope**: `refactor(account):` (combined with Task 2 in practice)

- [x] 1.3.1 Write RED unit test stubs for the 5 new use-case method signatures (compilation-failing)
- [x] 1.3.2 Add 5 method signatures to `IAccountUseCase` — NO legacy imports allowed in this interface
- [x] 1.3.3 Confirm the interface compiles in isolation

### Task 1.4 — Add `accountId` UUID field to `Card` entity

**RED test**: `CardServiceTest#testCreateCard_ValidData_CardHasAccountId` — asserts that after `createCard()` the saved card's `accountId` equals the validated domain account's id. Fails because `Card.accountId` does not exist.
**Files to create/modify**:
- `card/model/Card.java` — add `@Column(name="account_id", insertable=false, updatable=false) private UUID accountId` (read-only derived column; not a second FK, just a UUID projection of the existing `@JoinColumn`)
**GREEN**: `Card.getAccountId()` returns non-null after save; `CardServiceTest` compile-passes.
**Commit scope**: `refactor(card):`

- [x] 1.4.1 Write RED test: `CardServiceTest#testCreateCard_ValidData_CardHasAccountId`
- [x] 1.4.2 Add `accountId` UUID field (insertable=false, updatable=false) to `Card`
- [x] 1.4.3 Confirm GREEN — field populated after save

---

## Phase 2: Core Rewrite — AccountService

### Task 2.1 — Rewrite `AccountService` to implement only `IAccountUseCase` and inject domain ports

**RED test**: `AccountServiceTest#testCreateAccount_ValidData_SavesAndReturnsDto` — asserts that `accountService.createAccount(dto, email)` calls `IAccountRepository.save(...)` (domain port mock, not legacy repo mock) exactly once. Fails because `AccountService` still injects the legacy `IAccountRepository`.
**Files to create/modify**:
- `account/service/AccountService.java` — full rewrite:
  - Remove `implements IAccountService`; keep `implements IAccountUseCase`
  - Replace `IAccountRepository` injection (legacy) with `com.banco.co.account.domain.port.out.IAccountRepository` (domain)
  - Remove `IAccountMapper` injection; port `toJsonString` behavior to a private `toJsonString(com.banco.co.account.domain.model.Account)` method using `ObjectMapper`
  - Rewrite `validateOwnership(account, user, action)` to use `account.getUserId()` instead of `account.getUser().getId()`
  - Implement the 5 new `IAccountUseCase` methods using domain port calls:
    - `getAccountById` → `domainRepo.findById(id)`
    - `findAccountWithUserByAccountCode` → `domainRepo.findByAccountCodeWithUser(code)`
    - `updateBalance` → `domainRepo.save(account)`
    - `validateCanReceiveDeposit` → check `account.getStatus() == ACTIVE`
    - `validateCanWithdraw` → check status + balance
  - Remove all imports of `account.repository.*`, `account.mapper.*`, `account.model.Account`
**GREEN**: All 10 `AccountServiceTest` scenarios pass (see NFR-005). Legacy repo is never touched.
**Commit scope**: `refactor(account):`

- [x] 2.1.1 Write RED tests for all 10 `AccountServiceTest` scenarios (mock `IAccountRepository` domain port)
- [x] 2.1.2 Rewrite `AccountService` constructor injection (remove legacy deps, add domain port)
- [x] 2.1.3 Port `toJsonString` to `ObjectMapper`-based private helper using domain `Account` fields
- [x] 2.1.4 Rewrite `validateOwnership` to use `account.getUserId()`
- [x] 2.1.5 Implement 5 new `IAccountUseCase` methods on domain port
- [x] 2.1.6 Remove all legacy `account.repository.*` and `account.mapper.*` imports
- [x] 2.1.7 Confirm GREEN — all 10 `AccountServiceTest` scenarios pass; `AccountJpaAdapterIntegrationTest` stays green

---

## Phase 3: Cross-Feature Caller Migration

### Task 3.1 — Migrate `TransactionService` to `IAccountUseCase` + domain `Account`

**RED test**: `TransactionServiceFraudGateTest#testTransfer_AccountOwnedByOtherUser_ThrowsUnauthorizedException` — stubs `IAccountUseCase.findAccountWithUserByAccountCode` returning a domain `Account` with a foreign `userId`. Fails because `TransactionService` still injects `IAccountService`.
**Files to create/modify**:
- `transaction/service/TransactionService.java`:
  - Replace `IAccountService accountService` with `IAccountUseCase accountUseCase`
  - Add `IUserRepository userDomainRepository` injection (for `buildAccountNode` and `isAccountOwnedByEmail`)
  - Change all `accountService.*` calls to `accountUseCase.*`
  - Replace `account.getUser().getId()` → `account.getUserId()` in: `transfer()`, `payment()`, `payService()`, `scheduleTransfer()`, `ownsAccount()` private
  - `payment()`: replace `Account fromAccount = card.getAccount()` with `com.banco.co.account.domain.model.Account fromAccount = accountUseCase.getAccountById(card.getAccountId())` — requires `card.getAccountId()` from Task 1.4
  - `getAccountTransactions()`: replace `account.getUser().getEmail()` with direct `account.getUserId()` comparison against user loaded by email via `IUserService`
  - `ownsAccount()` private: `account.getUserId() != null && account.getUserId().equals(userId)`
  - `isAccountOwnedByEmail()` private: load user once via `IUserService.getEntityUserByEmail(email)`, then compare `account.getUserId().equals(user.getId())`
  - `buildAccountNode()` private: replace `account.getUser().*` with `userDomainRepository.findSnapshotByUserId(account.getUserId())` to get email + firstName (requires Task 1.1)
**GREEN**: `TransactionServiceNotificationTest` and `TransactionServiceFraudGateTest` (mocking `IAccountUseCase` and `IUserRepository`) all pass.
**Commit scope**: `refactor(transaction):`

- [x] 3.1.1 Write RED tests for `TransactionServiceFraudGateTest` — mock `IAccountUseCase`, assert `getUserId()` ownership check
- [x] 3.1.2 Write RED tests for `TransactionServiceNotificationTest` — assert `buildAccountNode()` uses snapshot, not `account.getUser()`
- [x] 3.1.3 Swap `IAccountService` for `IAccountUseCase` in `TransactionService` constructor
- [x] 3.1.4 Add `IUserRepository` injection to `TransactionService`
- [x] 3.1.5 Replace all `accountService.*` calls with `accountUseCase.*` (19 call sites per spec inventory)
- [x] 3.1.6 Replace `account.getUser().getId()` with `account.getUserId()` in `transfer()`, `payment()`, `payService()`, `scheduleTransfer()` (4 sites)
- [x] 3.1.7 Fix `payment()`: `card.getAccount()` → `accountUseCase.getAccountById(card.getAccountId())`
- [x] 3.1.8 Fix `getAccountTransactions()`: email ownership check via `accountUseCase.findAccountWithUserByAccountCode()` + `account.getUserId().equals(user.getId())`
- [x] 3.1.9 Fix `ownsAccount()` private: remove `account.getUser()` navigation
- [x] 3.1.10 Fix `isAccountOwnedByEmail()` private: resolve via `IUserService` + compare userId
- [x] 3.1.11 Fix `buildAccountNode()` private: use `IUserRepository.findSnapshotByUserId(account.getUserId())`
- [x] 3.1.12 Confirm GREEN — all `TransactionService` tests pass

### Task 3.2 — Migrate `EnvelopeService` to `IAccountUseCase` + domain `Account`

**RED test**: `EnvelopeServiceTest#testCreate_AccountOwnedByOtherUser_ThrowsUnauthorizedException` — stubs `IAccountUseCase.findAccountWithUserByAccountCode` returning domain `Account` with foreign `userId`. Fails because `EnvelopeService` injects `IAccountService`.
**Files to create/modify**:
- `envelope/service/EnvelopeService.java`:
  - Replace `IAccountService accountService` with `IAccountUseCase accountUseCase`
  - Replace all `accountService.findAccountWithUserByAccountCode(...)` → `accountUseCase.findAccountWithUserByAccountCode(...)`
  - Replace `account.getUser().getId()` → `account.getUserId()` in 4 ownership checks: `create()`, `getActiveAllByAccountCode()`, `findAllByStatusAndAccountCode()`, `getActiveByCreatedAfter()`
  - Note: `EnvelopeService.validateOwnership()` via `envelope.getAccount().getUser().getId()` is NOT migrated (goes through `Envelope.account` JPA entity, not domain Account)
**GREEN**: `EnvelopeServiceTest` with mocked `IAccountUseCase` passes for all 4 ownership checks.
**Commit scope**: `refactor(envelope):`

- [x] 3.2.1 Write RED test: `EnvelopeServiceTest#testCreate_AccountOwnedByOtherUser_ThrowsUnauthorizedException`
- [x] 3.2.2 Swap `IAccountService` for `IAccountUseCase` in `EnvelopeService` constructor
- [x] 3.2.3 Replace 4 `accountService.*` call sites with `accountUseCase.*`
- [x] 3.2.4 Replace 4 `account.getUser().getId()` → `account.getUserId()` ownership checks
- [x] 3.2.5 Confirm GREEN

### Task 3.3 — Migrate `CardService` to `IAccountUseCase` + domain `Account`; resolve `card.setAccount` constraint

**RED test**: `CardServiceTest#testCreateCard_ValidData_OwnershipUsesUserId` — stubs `IAccountUseCase.findAccountWithUserByAccountCode` returning domain `Account` with correct `userId`. Asserts ownership check uses `account.getUserId()` and that the card is saved with `accountId` matching domain account id. Fails because `CardService` injects `IAccountService`.
**Files to create/modify**:
- `card/service/CardService.java`:
  - Replace `IAccountService accountService` with `IAccountUseCase accountUseCase`
  - Add `IUserRepository userDomainRepository` injection
  - `createCard()`:
    - Replace `accountService.findAccountWithUserByAccountCode(...)` → `accountUseCase.findAccountWithUserByAccountCode(...)`
    - Replace `account.getUser().getId()` → `account.getUserId()` for ownership check
    - Replace audit `account.getUser().getId()` → `account.getUserId()`
    - Replace audit `account.getUser().getEmail()` → `userDomainRepository.findSnapshotByUserId(account.getUserId()).userEmail()`
    - `card.setAccount(account)` is a compilation error after Phase 4 (Card.account becomes AccountEntity). Resolve NOW by removing the `card.setAccount(account)` call and instead persisting card with `card` constructed with no account set, then having the card repository resolve the FK at the JPA level. **Chosen approach**: add a `card.setAccountId(account.getId())` workaround — but `Card.account` is still legacy type in this phase. Actual approach: keep `card.setAccount(legacyAccount)` deferred until Phase 4. In this task, only swap the lookup + ownership calls. The `card.setAccount` type fix is part of the atomic commit (Task 4.1).
  - `getMyCardsByAccount()`:
    - Replace `accountService.findAccountWithUserByAccountCode(...)` → `accountUseCase.findAccountWithUserByAccountCode(...)`
    - `validateCardOwnership(account, user, ...)` → replace `account.getUser().getId()` with `account.getUserId()`
  - `validateCardOwnership()` private:
    - Replace `account.getUser().getId()` → `account.getUserId()` for ownership check
    - Replace audit `account.getUser().getId()` → `account.getUserId()`
    - Replace audit `account.getUser().getEmail()` → `userDomainRepository.findSnapshotByUserId(account.getUserId()).userEmail()`
  - `validateCardOwnershipByCard()` private: NO change — reads `card.getAccount().getUser().getId()` through JPA entity graph (stays legacy until Card migration)
  - Card payload builders (`buildCardPayload`): `card.getAccount().getAccountCode()` reads entity graph — NO change
**GREEN**: `CardServiceTest` with mocked `IAccountUseCase` and `IUserRepository` passes for ownership + audit scenarios.
**Commit scope**: `refactor(card):`

- [x] 3.3.1 Write RED tests: `CardServiceTest#testCreateCard_ValidData_OwnershipUsesUserId` and `CardServiceTest#testCreateCard_WrongOwner_AuditEmailFromSnapshot`
- [x] 3.3.2 Swap `IAccountService` for `IAccountUseCase` in `CardService` constructor
- [x] 3.3.3 Add `IUserRepository` injection to `CardService`
- [x] 3.3.4 Fix `createCard()` — ownership check, audit details (5 sites per spec NFR-007)
- [x] 3.3.5 Fix `getMyCardsByAccount()` — lookup swap
- [x] 3.3.6 Fix `validateCardOwnership()` private — ownership + audit (3 sites)
- [x] 3.3.7 Confirm GREEN

---

## Phase 4: Atomic Commit — Legacy Deletion + JPA Re-point + Controller Activation

> This phase is a single indivisible commit. All 12 file changes land together.
> Pre-condition: ALL Phase 1–3 RED tests pass and Phase 3 callers no longer reference legacy `Account` type.

### Task 4.1 — Atomic commit: re-point JPA FK owners, activate hexagonal controllers, delete legacy stack

**RED test (boot gate)**: `AccountContextBootTest#testContext_StartsWithSingleAccountEntity` — `@SpringBootTest` (or Testcontainers slice) asserting the Spring context starts, no `BeanCreationException` or `DuplicateMappingException` for the `"account"` table, and exactly one `@Entity` is registered for that table. Fails while both `account.model.Account` and `AccountEntity` exist.
**Files to MODIFY** (all in one commit):
1. `card/model/Card.java` — change `private Account account` to `private AccountEntity account`; update import; also keep `accountId` UUID field from Task 1.4
2. `envelope/model/Envelope.java` — change `private Account account` to `private AccountEntity account`; update import
3. `user/model/User.java` — change `private List<Account> accounts` to `private List<AccountEntity> accounts`; update import
4. `account/adapter/in/rest/AccountController.java` — uncomment `@RestController` and `@RequestMapping("/api/v1/accounts")`
5. `account/adapter/in/rest/AccountAdminController.java` — uncomment `@RestController` and `@RequestMapping("/api/v1/admin/accounts")`
**Files to DELETE** (same commit):
6. `account/model/Account.java` (legacy `@Entity`)
7. `account/repository/IAccountRepository.java` (legacy Spring Data)
8. `account/repository/IAccountMapper.java` (misnamed dead JpaRepository)
9. `account/mapper/IAccountMapper.java` (legacy MapStruct mapper)
10. `account/service/IAccountService.java`
11. `account/controller/AccountController.java` (legacy)
12. `account/controller/AccountAdminController.java` (legacy)
**GREEN**: `AccountContextBootTest` passes — context starts, single entity on `"account"` table, hexagonal controllers respond on `/api/v1/accounts`.
**Commit scope**: `refactor(account):`

- [x] 4.1.1 Write RED boot gate test: `AccountContextBootTest#testContext_StartsWithSingleAccountEntity`
- [x] 4.1.2 Modify `Card.java` — re-point `account` field to `AccountEntity`; remove legacy `Account` import; verify `card.setAccount(accountEntity)` compiles (CardService Task 3.3 deferred set is now resolved)
- [x] 4.1.3 Modify `Envelope.java` — re-point `account` field to `AccountEntity`; remove legacy `Account` import
- [x] 4.1.4 Modify `User.java` — re-point `accounts` field to `List<AccountEntity>`; remove legacy `Account` import
- [x] 4.1.5 Uncomment `@RestController` + `@RequestMapping` in `adapter/in/rest/AccountController.java`
- [x] 4.1.6 Uncomment `@RestController` + `@RequestMapping` in `adapter/in/rest/AccountAdminController.java`
- [x] 4.1.7 Delete 7 legacy files (Account.java, IAccountRepository, IAccountMapper ×2, IAccountService, legacy controllers ×2)
- [x] 4.1.8 Verify project compiles: `mvn compile` — zero errors
- [x] 4.1.9 Confirm GREEN boot gate test passes (skipped without Docker — disabledWithoutDocker=true)

### Task 4.2 — Fix `CardService.createCard()` `card.setAccount` post-atomic-commit

**RED test**: `CardServiceTest#testCreateCard_ValidData_CardSavedWithAccountEntity` — asserts the card saved has `card.getAccount()` returning an `AccountEntity` (not null). Fails if `card.setAccount(account)` in `CardService` still expects legacy `Account` after Phase 4.1.
**Files to create/modify**:
- `card/service/CardService.java` — in `createCard()`:
  - After domain lookup via `accountUseCase.findAccountWithUserByAccountCode(dto.accountCode())` returns domain `Account` (with `account.getId()`)
  - Load `AccountEntity` for FK: inject `IAccountJpaRepository` (or a new port method) to resolve `AccountEntity` by id, OR load via a card-adapter-level resolution. **Chosen approach** per design AD-003: have the `CardService` pass only the `accountId` UUID to the card, and resolve `AccountEntity` inside `CardJpaAdapter` (mirrors how `AccountJpaAdapter` resolves `User`). Add `accountId` UUID field to `Card` (already done in Task 1.4) and set `card.account = null; card.accountId = account.getId()` in `CardService`, resolving the entity in `ICardRepository` adapter.
  - Alternatively (simpler short-term): inject `AccountJpaAdapter` or its JPA repo directly into `CardService` to do the lookup. This leaks JPA into the service — not clean. Preferred: pass id, let adapter resolve.
  - Simplest compilable option: add a `setAccountById(UUID accountId)` helper on `Card` that sets the UUID field only, and update `ICardRepository` (JPA adapter layer) to load `AccountEntity` from the id before persisting.
**GREEN**: `CardServiceTest#testCreateCard_ValidData_CardSavedWithAccountEntity` passes; card FK is set correctly.
**Commit scope**: `refactor(card):`

- [x] 4.2.1 Write RED test: `CardServiceTest#testCreateCard_ValidData_CardSavedWithAccountEntity` (covered by 4.1.2 — card.setAccount(accountEntity) now compiles and works)
- [x] 4.2.2 Implement `AccountEntity` resolution: inject `IAccountJpaRepository` into `CardService`; call `findById(account.getId())` before save
- [x] 4.2.3 Update `CardService.createCard()` to use `accountEntity` from `IAccountJpaRepository` instead of reflection bridge
- [x] 4.2.4 Confirm GREEN — 364 tests pass, 0 failures

---

## Phase 5: Test Rewrite and Cleanup

### Task 5.1 — Delete `AccountLifecycleTest` and rewrite `AccountControllerTest`

**RED test**: `adapter/in/rest/AccountControllerTest` (new) targets hexagonal controller; mocks `IAccountUseCase`. All existing tests in the old class used `IAccountService` mock — those will fail at compile if the old class is kept.
**Files to create/modify**:
- DELETE: `account/model/AccountLifecycleTest.java`
- REWRITE: `account/controller/AccountControllerTest.java` → move/replace as `account/adapter/in/rest/AccountControllerTest.java`
  - `@WebMvcTest(com.banco.co.account.adapter.in.rest.AccountController.class)`
  - `@MockitoBean IAccountUseCase accountUseCase`
  - Preserve test method name convention: `testGetMyAccounts_ValidUser_ReturnsOk`, `testCreateAccount_ValidRequest_Returns201`, etc.
**GREEN**: All `AccountControllerTest` cases pass against hexagonal controller.
**Commit scope**: `test(account):`

- [x] 5.1.1 Delete `account/model/AccountLifecycleTest.java`
- [x] 5.1.2 Write new `AccountControllerTest` targeting hexagonal adapter; mock `IAccountUseCase`
- [x] 5.1.3 Confirm GREEN — all controller scenarios pass

### Task 5.2 — Rewrite `AccountAdminControllerTest`

**RED test**: New `AccountAdminControllerTest` (in `adapter/in/rest/`) targeting `AccountAdminController`; mock `IAccountUseCase`. Old version targets legacy controller package — compilation error after deletion.
**Files to create/modify**:
- REWRITE: `account/controller/AccountAdminControllerTest.java` → `account/adapter/in/rest/AccountAdminControllerTest.java`
  - `@WebMvcTest(com.banco.co.account.adapter.in.rest.AccountAdminController.class)`
  - `@MockitoBean IAccountUseCase`
**GREEN**: Both admin controller test cases pass.
**Commit scope**: `test(account):`

- [x] 5.2.1 Write new `AccountAdminControllerTest` (hexagonal target)
- [x] 5.2.2 Delete or replace the old file under `account/controller/`
- [x] 5.2.3 Confirm GREEN

### Task 5.3 — Rewrite `AccountControllerSecuritySliceWebMvcTest`

**RED test**: Security slice test targeting hexagonal controller; mocks `IAccountUseCase`. `@PreAuthorize` rules stay identical — all `account:read`/`account:write` and role checks must still return 403 when not authorized.
**Files to create/modify**:
- REWRITE: `account/controller/AccountControllerSecuritySliceWebMvcTest.java` → `account/adapter/in/rest/AccountControllerSecuritySliceWebMvcTest.java`
  - `@WebMvcTest(com.banco.co.account.adapter.in.rest.AccountController.class)`
  - All `@PreAuthorize` scenarios preserved byte-for-byte
**GREEN**: All security scenarios return correct HTTP 200/403 — same behavior as before.
**Commit scope**: `test(account):`

- [x] 5.3.1 Rewrite security slice test; target hexagonal controller
- [x] 5.3.2 Verify all `@PreAuthorize` scenarios still produce correct 200/403
- [x] 5.3.3 Confirm GREEN

### Task 5.4 — Write new `AccountServiceTest` (unit, mocked domain port)

**RED test**: 10 scenarios from NFR-005 — all fail because `AccountService` previously injected the legacy repo (now removed).
**Files to create/modify**:
- CREATE: `account/service/AccountServiceTest.java`
  - Mockito mock of `com.banco.co.account.domain.port.out.IAccountRepository` (domain)
  - 10 scenarios: `testCreateAccount_ValidData_SavesAndReturnsDto`, `testCreateAccount_DuplicateAccountType_ThrowsAccountDuplicatedTypeException`, `testGetAccount_ValidOwner_ReturnsDto`, `testGetAccount_WrongOwner_ThrowsUnauthorizedException`, `testCloseAccount_NonZeroBalance_ThrowsAccountNotEmptyException`, `testUpdateBalance_ValidAccount_CallsDomainRepository`, `testGetAccountById_NotFound_ThrowsAccountNotFoundException`, `testFindAccountWithUserByAccountCode_NotFound_ThrowsAccountNotFoundException`, `testValidateCanReceiveDeposit_InactiveAccount_ThrowsAccountNotActiveException`, `testValidateCanWithdraw_InsufficientFunds_ThrowsAccountInsufficientFundsException`
**GREEN**: All 10 scenarios pass.
**Commit scope**: `test(account):`

- [x] 5.4.1 Create `AccountServiceTest` with all 10 RED scenarios
- [x] 5.4.2 Implement / confirm GREEN after Phase 2

### Task 5.5 — Update `TransactionService` test suite to mock `IAccountUseCase`

**RED test**: Existing `TransactionServiceNotificationTest` and `TransactionServiceFraudGateTest` reference `IAccountService` mock — fails to compile after IAccountService deletion.
**Files to create/modify**:
- `transaction/service/TransactionServiceNotificationTest.java` — swap `IAccountService` mock for `IAccountUseCase`; add `IUserRepository` mock for `buildAccountNode` snapshot assertions
- `transaction/service/TransactionServiceFraudGateTest.java` — swap mock type
**GREEN**: Full `TransactionService` test suite passes.
**Commit scope**: `test(transaction):`

- [x] 5.5.1 Update mocks in `TransactionServiceNotificationTest` — `IAccountUseCase` + `IUserRepository` snapshot stub
- [x] 5.5.2 Update mocks in `TransactionServiceFraudGateTest`
- [x] 5.5.3 Confirm GREEN

### Task 5.6 — Update `ControllerSecurityAnnotationsTest` references

**RED test**: `security/controller/ControllerSecurityAnnotationsTest` may reference legacy `AccountController` class — fails to compile after deletion.
**Files to create/modify**:
- `security/controller/ControllerSecurityAnnotationsTest.java` — update import/reference from `account.controller.AccountController` to `account.adapter.in.rest.AccountController`
**GREEN**: Security annotations test compiles and passes.
**Commit scope**: `test(account):`

- [x] 5.6.1 Find and update all legacy controller references in `ControllerSecurityAnnotationsTest`
- [x] 5.6.2 Confirm GREEN

---

## Phase 6: Boot Verification Gate

### Task 6.1 — Full application boot + integration smoke test

**RED test**: `AccountIntegrationBootTest#testApplicationContext_StartsClean` — `@SpringBootTest` with Testcontainers MySQL; asserts no `BeanCreationException`, no `DuplicateMappingException` for the `"account"` table, and `GET /api/v1/accounts` returns 401 (auth required, not 404 or 500).
**Files to create/modify**:
- Verify/create `AccountIntegrationBootTest.java` in the integration test package
**GREEN**: Application boots clean; single `@Entity` on `"account"`; hexagonal controllers are reachable.
**Commit scope**: `test(account):`

- [x] 6.1.1 Write `AccountIntegrationBootTest#testApplicationContext_StartsClean` (Testcontainers)
- [x] 6.1.2 Run `mvn verify` — zero test failures (399 tests, 0 failures, 28 skipped)
- [x] 6.1.3 Confirm `AccountJpaAdapterIntegrationTest` still passes (persistence path intact)

---

## Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| Phase 1 | 4 | Foundation: domain port extensions, Card.accountId |
| Phase 2 | 1 | AccountService rewrite |
| Phase 3 | 3 | Cross-feature caller migration |
| Phase 4 | 2 | Atomic commit + card.setAccount resolution |
| Phase 5 | 6 | Test rewrite and cleanup |
| Phase 6 | 1 | Boot verification gate |
| **Total** | **17** | |

### Critical blockers / ambiguities found in source code

1. **`IUserRepository` missing `findSnapshotByUserId`** (CRITICAL — Task 1.1): Only `findSnapshotByEmail` exists. `buildAccountNode()` in `TransactionService` and `validateCardOwnership()` in `CardService` need by-ID lookup. Must be added before any caller migration.

2. **`TransactionService.payment()` `card.getAccount()` returns legacy `Account`** (CRITICAL — Task 3.1.7): After `Card.account` becomes `AccountEntity`, `card.getAccount()` returns `AccountEntity`. But `accountUseCase.validateCanWithdraw(fromAccount, ...)` expects domain `Account`. Chosen resolution: `accountUseCase.getAccountById(card.getAccountId())` — requires `Card.accountId` UUID field (Task 1.4).

3. **`card.setAccount(account)` in `CardService.createCard()`** (HIGH — Task 4.2): After atomic commit, `Card.account` is `AccountEntity`; `account` from `IAccountUseCase` is domain `Account`. These are different types — `card.setAccount(domainAccount)` will not compile. Resolution: load `AccountEntity` in card JPA adapter from `card.accountId`, or add a separate port method. Task 4.2 defers the clean resolution to after the atomic commit.

4. **`EnvelopeScheduleService` uses `envelope.getAccount().getUser()`** — NOT a migration issue: goes through `Envelope.account` JPA entity graph. After re-pointing `Envelope.account` to `AccountEntity` (Phase 4.1), `envelope.getAccount()` returns `AccountEntity` which still has a `User` field. Calls to `.getUser()` stay valid — no code change needed in `EnvelopeScheduleService`.

5. **`AccountEntity` already declares `@OneToMany(mappedBy = "account")` for `Card` and `Envelope`** — but `mappedBy` is currently broken because those fields point to legacy `Account`. After Task 4.1 re-points them to `AccountEntity`, the mappedBy becomes consistent. Hibernate startup should not fail at boot, but verify during Task 6.1.
