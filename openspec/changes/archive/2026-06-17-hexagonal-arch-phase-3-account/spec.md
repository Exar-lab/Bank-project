# Spec — hexagonal-arch-phase-3-account

**Phase**: spec  
**Status**: ready  
**Depends on**: proposal (openspec/changes/hexagonal-arch-phase-3-account/proposal.md)  
**Next**: design, tasks

---

## Scope Summary

Complete the hexagonal contraction for the `account` feature: extend the inbound port, rewire the service to the domain output port, migrate three cross-feature callers, atomically activate the hexagonal REST adapters and delete the legacy stack, and rewrite all tests against the hexagonal types.

---

## NFR-001 — IAccountUseCase completeness

`com.banco.co.account.domain.port.in.IAccountUseCase` MUST expose the following five methods after this change is applied. Return types use the domain model `com.banco.co.account.domain.model.Account`, not the legacy `com.banco.co.account.model.Account`.

```java
// Lookup by UUID — no ownership check (internal use)
com.banco.co.account.domain.model.Account getAccountById(UUID accountId);

// Lookup by account code with user context — no ownership check (internal use)
com.banco.co.account.domain.model.Account findAccountWithUserByAccountCode(String accountCode);

// Persist balance changes after fund mutations
void updateBalance(com.banco.co.account.domain.model.Account account);

// Assert account status == ACTIVE; throws AccountNotActiveException otherwise
void validateCanReceiveDeposit(com.banco.co.account.domain.model.Account account);

// Assert status == ACTIVE and balance >= amount; throws on failure
void validateCanWithdraw(com.banco.co.account.domain.model.Account account, BigDecimal amount);
```

**Constraint**: the signatures above must be identical to the existing `IAccountService` counterparts except for the return/parameter type swap (`domain.model.Account` in place of `model.Account`).

**Constraint**: `IAccountUseCase` MUST NOT import any class from `com.banco.co.account.model.*`, `com.banco.co.account.service.*`, or any JPA/Spring Data package.

### Scenarios — NFR-001

**Scenario 1.1 — All five methods present**
```
Given: IAccountUseCase.java is compiled
When:  A caller (e.g., TransactionService) invokes getAccountById(UUID)
Then:  The method is found at compile time with return type domain.model.Account
```

**Scenario 1.2 — No legacy Account type in port**
```
Given: IAccountUseCase.java is compiled
When:  Static analysis checks imports
Then:  No import of com.banco.co.account.model.Account is present
```

**Scenario 1.3 — findAccountWithUserByAccountCode populates userId**
```
Given: An account exists in the database with an associated user UUID
When:  IAccountUseCase.findAccountWithUserByAccountCode(accountCode) is called
Then:  The returned domain Account has a non-null userId equal to the owning user's UUID
```

---

## NFR-002 — AccountService rewired to domain output port

`com.banco.co.account.service.AccountService` MUST:

1. Inject `com.banco.co.account.domain.port.out.IAccountRepository` (not the legacy Spring Data `com.banco.co.account.repository.IAccountRepository`).
2. No longer implement `IAccountService`; implement only `IAccountUseCase`.
3. Delegate ALL persistence calls through the domain output port `IAccountRepository`.
4. Retain `ObjectMapper`-based JSON serialization for audit-log payload building (the `toJsonString` behavior currently handled by the legacy `IAccountMapper` must be ported inline or to a private helper, since the legacy mapper is deleted).

**Constraint**: After the change, `AccountService` MUST NOT import `com.banco.co.account.repository.*` or `com.banco.co.account.mapper.*`.

**Constraint**: The domain `IAccountRepository` output port MUST include a method equivalent to `findAccountWithUser(String accountCode)` (currently only `findByAccountCode` exists). This method is required by `AccountService.findAccountWithUserByAccountCode()`. **This is a discovered gap** — the domain port must be extended as part of this change.

Specifically, `com.banco.co.account.domain.port.out.IAccountRepository` must add:

```java
Optional<com.banco.co.account.domain.model.Account> findByAccountCodeWithUser(String accountCode);
```

And the `AccountJpaAdapter` must implement it (mapping the legacy `findAccountWithUser` JPQL query result to the domain model).

**Constraint**: `AccountService` must use the `IUserRepository` domain port for user snapshot lookups where applicable, and may retain `IUserService` for cases where the full `User` entity is required (audit log, ownership checks during the transition to domain User type).

### Scenarios — NFR-002

**Scenario 2.1 — Service wired to domain port**
```
Given: The Spring context starts without the legacy account repository bean
When:  AccountService is instantiated
Then:  It receives com.banco.co.account.domain.port.out.IAccountRepository via constructor injection
       and no NoSuchBeanDefinitionException is thrown
```

**Scenario 2.2 — Persistence flows through AccountJpaAdapter**
```
Given: A call to AccountService.createAccount() is made
When:  The account is saved
Then:  AccountJpaAdapter.save() is invoked (verifiable via integration test)
       and the account is persisted to the "account" table through AccountEntity
```

**Scenario 2.3 — Audit-log JSON is equivalent**
```
Given: An account update is performed
When:  The audit log entry is written
Then:  The "newValues" field contains JSON with at least: id, accountCode, accountType, status, balance
       (same fields as the legacy IAccountMapper.toJsonString() produced)
```

**Scenario 2.4 — IAccountService is gone**
```
Given: The change is applied
When:  The codebase is compiled
Then:  com.banco.co.account.service.IAccountService does not exist
       and no class implements it
```

---

## NFR-003 — Cross-feature callers migrated to IAccountUseCase

`TransactionService`, `EnvelopeService`, and `CardService` MUST inject `IAccountUseCase` instead of `IAccountService`. All calls to `account.getUser().getId()` or `account.getUser().getEmail()` MUST be replaced with `account.getUserId()` or a `IUserRepository` lookup where the email is needed.

### Exact call-site inventory

#### TransactionService — calls to IAccountService

| Call site | Method | Current | After |
|-----------|--------|---------|-------|
| `transfer()` | ownership check | `fromAccount.getUser().getId()` | `fromAccount.getUserId()` |
| `transfer()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same method on `IAccountUseCase` |
| `transfer()` | validation | `accountService.validateCanWithdraw(...)` | same on `IAccountUseCase` |
| `transfer()` | validation | `accountService.validateCanReceiveDeposit(...)` | same on `IAccountUseCase` |
| `transfer()` | update | `accountService.updateBalance(...)` | same on `IAccountUseCase` |
| `payment()` | ownership check | `fromAccount.getUser().getId()` | `fromAccount.getUserId()` |
| `payment()` | validation | `accountService.validateCanWithdraw(...)` | same on `IAccountUseCase` |
| `payment()` | update | `accountService.updateBalance(...)` | same on `IAccountUseCase` |
| `payService()` | ownership check | `fromAccount.getUser().getId()` | `fromAccount.getUserId()` |
| `payService()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `payService()` | validation | `accountService.validateCanWithdraw(...)` | same on `IAccountUseCase` |
| `cashDeposit()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `cashDeposit()` | validation | `accountService.validateCanReceiveDeposit(...)` | same on `IAccountUseCase` |
| `cashDeposit()` | update | `accountService.updateBalance(...)` | same on `IAccountUseCase` |
| `cashWithdrawal()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `cashWithdrawal()` | validation | `accountService.validateCanWithdraw(...)` | same on `IAccountUseCase` |
| `checkDeposit()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `checkDeposit()` | validation | `accountService.validateCanReceiveDeposit(...)` | same on `IAccountUseCase` |
| `scheduleTransfer()` | ownership check | `fromAccount.getUser().getId()` | `fromAccount.getUserId()` |
| `scheduleTransfer()` | lookups | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `getAccountTransactions()` | ownership | `account.getUser().getEmail()` | resolved via `IUserRepository` lookup by userId, then compare email; OR `account.getUserId()` compared against `user.getId()` (preferred — no extra lookup needed) |
| `approveTransaction()` | lookup | `accountService.getAccountById(...)` | same on `IAccountUseCase` |
| `reverseTransaction()` | lookup | `accountService.getAccountById(...)` | same on `IAccountUseCase` |
| `ownsAccount()` private | ownership | `account.getUser() != null && account.getUser().getId()` | `account.getUserId() != null && account.getUserId()` |
| `isAccountOwnedByEmail()` private | ownership | `account.getUser() != null && account.getUser().getEmail()` | requires userEmail resolution; replace by: `account.getUserId() != null && account.getUserId().equals(user.getId())` where `user` is retrieved once |
| `buildAccountNode()` private | payload build | `account.getUser().getId()`, `.getEmail()`, `.getFistName()` | MUST resolve via `IUserRepository.findSnapshotByUserId(account.getUserId())` or equivalent; UserSnapshot provides userId + email + name |

**Critical**: `buildAccountNode()` in `TransactionService` currently reads `account.getUser().getEmail()` and `account.getUser().getFistName()`. After migration, `domain.model.Account` has only `userId`. The outbox notification payload builder MUST be adapted to fetch user details from `IUserRepository` using `account.getUserId()`.

#### EnvelopeService — calls to IAccountService

| Call site | Method | Current | After |
|-----------|--------|---------|-------|
| `create()` | ownership | `account.getUser().getId()` | `account.getUserId()` |
| `create()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `getActiveAllByAccountCode()` | ownership | `account.getUser().getId()` | `account.getUserId()` |
| `getActiveAllByAccountCode()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `findAllByStatusAndAccountCode()` | ownership | `account.getUser().getId()` | `account.getUserId()` |
| `findAllByStatusAndAccountCode()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `getActiveByCreatedAfter()` | ownership | `account.getUser().getId()` | `account.getUserId()` |
| `getActiveByCreatedAfter()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |

**Note**: `EnvelopeService.validateOwnership()` currently reads `envelope.getAccount().getUser().getId()` — this operates on the legacy `Envelope.account` (which is the legacy `Account` entity with a `@ManyToOne User`). Since the `Envelope` entity is NOT migrated in this slice, this particular path does NOT change. Only the calls that go through `accountService.findAccountWithUserByAccountCode()` are migrated.

#### CardService — calls to IAccountService

| Call site | Method | Current | After |
|-----------|--------|---------|-------|
| `createCard()` | lookup | `accountService.findAccountWithUserByAccountCode(dto.accountCode())` | same on `IAccountUseCase` |
| `createCard()` | ownership | `account.getUser().getId()` | `account.getUserId()` |
| `createCard()` | audit log detail | `account.getUser().getId()`, `account.getUser().getEmail()` | resolve via `IUserRepository.findSnapshotByUserId(account.getUserId())` |
| `getMyCardsByAccount()` | lookup | `accountService.findAccountWithUserByAccountCode(...)` | same on `IAccountUseCase` |
| `getMyCardsByAccount()` | `validateCardOwnership(account, user, ...)` | `account.getUser().getId()` | `account.getUserId()` |
| `validateCardOwnership()` private | ownership | `account.getUser().getId()`, `account.getUser().getEmail()` | `account.getUserId()` for the ID check; for audit log details, resolve email/id from `IUserRepository` |

**Note**: `validateCardOwnershipByCard()` reads `card.getAccount().getUser().getId()`. Since `Card.account` is the legacy `Account` entity (not migrated in this slice), this path does NOT change.

### Scenarios — NFR-003

**Scenario 3.1 — TransactionService compiles without IAccountService**
```
Given: IAccountService is deleted
When:  TransactionService.java is compiled
Then:  No compilation error — all account calls target IAccountUseCase
```

**Scenario 3.2 — Ownership check uses userId not getUser().getId()**
```
Given: A transfer request from user A trying to use account owned by user B
When:  TransactionService.transfer() runs the ownership check
Then:  fromAccount.getUserId().equals(user.getId()) is evaluated (no getUser() call)
       and UnauthorizedException is thrown
```

**Scenario 3.3 — EnvelopeService ownership check migrated**
```
Given: User A requests an envelope on an account owned by user B
When:  EnvelopeService.create() runs
Then:  account.getUserId() is used in the ownership comparison
       and UnauthorizedException is thrown
```

**Scenario 3.4 — CardService compiles without IAccountService**
```
Given: IAccountService is deleted
When:  CardService.java is compiled
Then:  No compilation error — all account calls target IAccountUseCase
```

**Scenario 3.5 — Outbox notification payload for TransactionService**
```
Given: A transfer completes successfully
When:  buildAccountNode() is called with a domain Account
Then:  The notification outbox event contains userId, userEmail, and userFirstName
       resolved from IUserRepository (not from account.getUser())
```

---

## NFR-004 — Atomic activation and legacy deletion

The following files MUST be deleted and the hexagonal controllers MUST be activated in the **same commit**. No intermediate state where both exist active is permitted.

### Files to delete

- `banco-service/src/main/java/com/banco/co/account/model/Account.java` (legacy `@Entity`)
- `banco-service/src/main/java/com/banco/co/account/repository/IAccountRepository.java` (legacy Spring Data)
- `banco-service/src/main/java/com/banco/co/account/repository/IAccountMapper.java` (misnamed dead JpaRepository)
- `banco-service/src/main/java/com/banco/co/account/mapper/IAccountMapper.java` (legacy MapStruct mapper)
- `banco-service/src/main/java/com/banco/co/account/service/IAccountService.java`
- `banco-service/src/main/java/com/banco/co/account/controller/AccountController.java` (legacy)
- `banco-service/src/main/java/com/banco/co/account/controller/AccountAdminController.java` (legacy)

### Files to activate (uncomment annotations)

- `banco-service/src/main/java/com/banco/co/account/adapter/in/rest/AccountController.java`
  - Uncomment `@RestController`
  - Uncomment `@RequestMapping("/api/v1/accounts")`
- `banco-service/src/main/java/com/banco/co/account/adapter/in/rest/AccountAdminController.java`
  - Uncomment `@RestController`
  - Uncomment `@RequestMapping("/api/v1/admin/accounts")`

### Scenarios — NFR-004

**Scenario 4.1 — Single @Entity for account table**
```
Given: The change is applied
When:  The Spring application context starts
Then:  Exactly one @Entity maps to the "account" table (AccountEntity in adapter/out/jpa/)
       and no EntityMappingException or table conflict is thrown at startup
```

**Scenario 4.2 — Legacy files absent**
```
Given: The change is applied
When:  The filesystem is inspected
Then:  None of the 7 files listed above exist in the repository
```

**Scenario 4.3 — Hexagonal controllers active**
```
Given: The change is applied and the application starts
When:  GET /api/v1/accounts is called with a valid JWT
Then:  The request is handled by com.banco.co.account.adapter.in.rest.AccountController
       (not the legacy controller which no longer exists)
```

**Scenario 4.4 — Boot verification gate**
```
Given: The change is applied
When:  The Spring Boot application starts (./mvnw spring-boot:run or test slice startup)
Then:  No BeanCreationException, EntityMappingException, or DuplicateMappingException is logged
       related to the "account" table
```

---

## NFR-005 — Test coverage

### Tests to delete

- `banco-service/src/test/java/com/banco/co/account/model/AccountLifecycleTest.java`
  - Tests the legacy `@Entity` Account which is deleted. Must be removed.

### Tests to rewrite

All three files currently target `account/controller/` (legacy) and mock `IAccountService`. They MUST be rewritten to target `account/adapter/in/rest/` and mock `IAccountUseCase`.

- `banco-service/src/test/java/com/banco/co/account/controller/AccountControllerTest.java`
  → target: `com.banco.co.account.adapter.in.rest.AccountController`
  → mock: `IAccountUseCase`
  → keep: same test method names (test<Method>_<Condition>_<Expected> convention)

- `banco-service/src/test/java/com/banco/co/account/controller/AccountAdminControllerTest.java`
  → target: `com.banco.co.account.adapter.in.rest.AccountAdminController`
  → mock: `IAccountUseCase`

- `banco-service/src/test/java/com/banco/co/account/controller/AccountControllerSecuritySliceWebMvcTest.java`
  → target: `com.banco.co.account.adapter.in.rest.AccountController`
  → mock: `IAccountUseCase`
  → keep: all `@PreAuthorize` role/authority scenarios unchanged (same security rules apply)

### Tests to update

- All `TransactionService` tests that mock `IAccountService` must be updated to mock `IAccountUseCase` instead.

### New tests required

- `AccountServiceTest` — unit test for `AccountService` using a mocked `com.banco.co.account.domain.port.out.IAccountRepository` (domain port). Required scenarios:
  - `testCreateAccount_ValidData_SavesAndReturnsDto`
  - `testCreateAccount_DuplicateAccountType_ThrowsAccountDuplicatedTypeException`
  - `testGetAccount_ValidOwner_ReturnsDto`
  - `testGetAccount_WrongOwner_ThrowsUnauthorizedException`
  - `testCloseAccount_NonZeroBalance_ThrowsAccountNotEmptyException`
  - `testUpdateBalance_ValidAccount_CallsDomainRepository`
  - `testGetAccountById_NotFound_ThrowsAccountNotFoundException`
  - `testFindAccountWithUserByAccountCode_NotFound_ThrowsAccountNotFoundException`
  - `testValidateCanReceiveDeposit_InactiveAccount_ThrowsAccountNotActiveException`
  - `testValidateCanWithdraw_InsufficientFunds_ThrowsAccountInsufficientFundsException`

### Tests to preserve

- `banco-service/src/test/java/com/banco/co/account/domain/model/AccountTest.java` — pure domain model tests; no change needed.
- `banco-service/src/test/java/com/banco/co/account/adapter/out/jpa/AccountJpaAdapterIntegrationTest.java` — adapter integration tests; must remain green after the domain port extension (new `findByAccountCodeWithUser` method added).

### Scenarios — NFR-005

**Scenario 5.1 — No compilation errors in test suite**
```
Given: The change is applied
When:  mvn test-compile is run
Then:  Zero compilation errors
```

**Scenario 5.2 — AccountLifecycleTest deleted**
```
Given: The change is applied
When:  The test directory is inspected
Then:  account/model/AccountLifecycleTest.java does not exist
```

**Scenario 5.3 — Controller tests target hexagonal adapter**
```
Given: AccountControllerTest.java is compiled
When:  Static analysis checks @WebMvcTest target and mock types
Then:  @WebMvcTest targets com.banco.co.account.adapter.in.rest.AccountController
       and Mockito mocks IAccountUseCase (not IAccountService)
```

**Scenario 5.4 — Security slice test retains all @PreAuthorize scenarios**
```
Given: AccountControllerSecuritySliceWebMvcTest runs
When:  A request is made with a role that lacks the required authority
Then:  HTTP 403 is returned — same behavior as before the change
```

**Scenario 5.5 — AccountService unit tests pass with mocked domain port**
```
Given: AccountServiceTest uses a Mockito mock of IAccountRepository (domain port)
When:  testCreateAccount_ValidData_SavesAndReturnsDto runs
Then:  The mock's save() is called exactly once
       and the returned AccountResponseDto is non-null
```

---

## NFR-006 — Controller URL parity

The hexagonal adapters (`adapter/in/rest/`) MUST expose the **exact same** URL paths, HTTP verbs, request/response types, and `@PreAuthorize` expressions as the legacy controllers. No contract changes.

### Verified parity (from source inspection)

#### AccountController

| Verb | Path | @PreAuthorize | Response |
|------|------|---------------|----------|
| GET | `/api/v1/accounts` | `account:read` or CUSTOMER_BASIC/CUSTOMER_PREMIUM | `List<AccountResponseDto>` |
| GET | `/api/v1/accounts/{id}` | `account:read` or CUSTOMER_BASIC/CUSTOMER_PREMIUM | `AccountResponseDto` |
| GET | `/api/v1/accounts/code/{code}` | `account:read` or CUSTOMER_BASIC/CUSTOMER_PREMIUM | `AccountResponseDto` |
| GET | `/api/v1/accounts/{id}/balance` | `account:read` or CUSTOMER_BASIC/CUSTOMER_PREMIUM | `BigDecimal` |
| POST | `/api/v1/accounts` | `account:write` | `AccountResponseDto` (201 CREATED) |
| PUT | `/api/v1/accounts/{code}` | `account:write` | `AccountResponseDto` |
| DELETE | `/api/v1/accounts/{id}` | `account:write` | `204 NO CONTENT` |

**Verification**: source inspection confirms `adapter/in/rest/AccountController` matches legacy `controller/AccountController` on all 7 endpoints, verbs, status codes, and security expressions. Parity is confirmed — no fix required.

#### AccountAdminController

| Verb | Path | @PreAuthorize | Response |
|------|------|---------------|----------|
| PUT | `/api/v1/admin/accounts/{id}/status` | `account:write` AND (SYSTEM_ADMIN or SUPER_ADMIN) | `AccountResponseDto` |
| POST | `/api/v1/admin/accounts/{id}/close` | `account:write` AND (SYSTEM_ADMIN or SUPER_ADMIN) | `204 NO CONTENT` |

**Verification**: source inspection confirms `adapter/in/rest/AccountAdminController` matches legacy `controller/AccountAdminController` on both endpoints. Parity is confirmed — no fix required.

### Scenarios — NFR-006

**Scenario 6.1 — GET /api/v1/accounts returns same shape**
```
Given: The hexagonal controllers are active
When:  GET /api/v1/accounts is called with CUSTOMER_BASIC role
Then:  HTTP 200 is returned with List<AccountResponseDto> — same as legacy
```

**Scenario 6.2 — POST /api/v1/accounts returns 201**
```
Given: The hexagonal controllers are active
When:  POST /api/v1/accounts is called with valid AccountRequestDto
Then:  HTTP 201 CREATED is returned — same as legacy
```

**Scenario 6.3 — PUT /api/v1/admin/accounts/{id}/status rejects non-admin**
```
Given: The hexagonal controllers are active
When:  PUT /api/v1/admin/accounts/{id}/status is called with CUSTOMER_BASIC role
Then:  HTTP 403 FORBIDDEN is returned — same as legacy
```

---

## NFR-007 — CardService account call enumeration and migration path

### Complete enumeration of account-related calls in CardService

| Method | Call | Domain Account field | Migration |
|--------|------|---------------------|-----------|
| `createCard()` | `accountService.findAccountWithUserByAccountCode(dto.accountCode())` | — | call `accountUseCase.findAccountWithUserByAccountCode(dto.accountCode())` |
| `createCard()` | `account.getUser().getId()` | ownership check | `account.getUserId()` |
| `createCard()` | audit: `account.getUser().getId()` | log detail | `account.getUserId()` directly |
| `createCard()` | audit: `account.getUser().getEmail()` | log detail | resolve via `IUserRepository.findSnapshotByUserId(account.getUserId()).userEmail()` |
| `getMyCardsByAccount()` | `accountService.findAccountWithUserByAccountCode(accountCode)` | — | call `accountUseCase.findAccountWithUserByAccountCode(accountCode)` |
| `getMyCardsByAccount()` | `validateCardOwnership(account, user, ...)` → `account.getUser().getId()` | ownership | `account.getUserId()` |
| `validateCardOwnership()` | `account.getUser().getId()` | ownership | `account.getUserId()` |
| `validateCardOwnership()` | audit: `account.getUser().getId()`, `account.getUser().getEmail()` | log details | `account.getUserId()` for ID; resolve email via `IUserRepository` |

**Not migrated in this slice** (Card entity still holds legacy Account entity via `@ManyToOne`):
- `validateCardOwnershipByCard()` — reads `card.getAccount().getUser().getId()` — stays as-is (Card is NOT hexagonally migrated in this change)
- `findCardWithAccountByCardCode()` — card repository call — no change

### Scenarios — NFR-007

**Scenario 7.1 — CardService.createCard ownership check uses userId**
```
Given: IAccountService is deleted
When:  CardService.createCard() runs the ownership check
Then:  account.getUserId().equals(user.getId()) is evaluated
       and no getUser() call is made on the domain Account
```

**Scenario 7.2 — CardService audit log resolves user details from IUserRepository**
```
Given: createCard() is called by user with email "user@test.com"
When:  The audit log failure detail for "ownerEmail" is built
Then:  The email is resolved from IUserRepository.findSnapshotByUserId(account.getUserId())
       not from account.getUser().getEmail()
```

---

## Discovered Issues (spec-level findings)

### ISSUE-1 (Critical) — Domain IAccountRepository missing findByAccountCodeWithUser

`com.banco.co.account.domain.port.out.IAccountRepository` does not have a method equivalent to the legacy `IAccountRepository.findAccountWithUser(String accountCode)`. This method is called by `AccountService.findAccountWithUserByAccountCode()` which is in turn called by TransactionService, EnvelopeService, and CardService on every ownership-sensitive operation.

**Impact**: Without this method, `AccountService` cannot be rewired to the domain port while preserving the "find account with user context" semantic required by callers.

**Resolution**: Add `Optional<Account> findByAccountCodeWithUser(String accountCode)` to `IAccountRepository` (domain port) and implement it in `AccountJpaAdapter`. The implementation maps the existing JPQL `findAccountWithUser` query result to the domain model.

### ISSUE-2 (High) — TransactionService.buildAccountNode() requires user details not on domain Account

`TransactionService.buildAccountNode()` currently reads `account.getUser().getEmail()` and `account.getUser().getFistName()` to build outbox notification payloads. After migrating to `domain.model.Account`, only `account.getUserId()` is available.

**Resolution**: `TransactionService` must inject `IUserRepository` (domain port) alongside `IAccountUseCase` and call `IUserRepository.findSnapshotByUserId(account.getUserId())` to retrieve `UserSnapshot.userEmail()` and `UserSnapshot.userName()` (or equivalent) for the notification payload.

### ISSUE-3 (High) — TransactionService.isAccountOwnedByEmail() requires email resolution

`isAccountOwnedByEmail(String accountCode, String userEmail)` calls `account.getUser().getEmail()`. After migration, this must be replaced by comparing `account.getUserId()` against a user looked up by email via `IUserService.getEntityUserByEmail(userEmail).getId()` — or simplified to ID comparison where the caller already has the user entity.

**Resolution**: Refactor private helper to accept `UUID userId` instead of email, and resolve the user ID once at the call site (which already loads the user via `IUserService`).

### ISSUE-4 (Medium) — CardService audit log for ownership violation reads account.getUser().getEmail()

In `validateCardOwnership()`, the audit detail `ownerEmail` is built from `account.getUser().getEmail()`. After migration, this requires a `IUserRepository` lookup.

**Resolution**: inject `IUserRepository` into `CardService` and resolve owner email from snapshot for the audit detail only.

---

## Out of Scope (confirmed)

- Hexagonal migration of `card`, `envelope` features (except the caller-injection swap specified above).
- URL or DTO contract changes.
- Flyway/schema migrations.
- Introduction of `IAccountQueryPort` (rejected in proposal).
- Keeping `IAccountService` facade (rejected in proposal).

---

## Assumptions

1. `account.getUserId()` is always populated after a `findByAccountCodeWithUser` call (the JpaAdapter maps the user FK to `userId` on the domain model). Verified: `AccountEntity` has a `user_id` FK; the adapter must set `domain.setUserId(entity.getUser().getId())` in its mapping.
2. `IUserRepository.findSnapshotByUserId(UUID)` exists or will be added — the `transaction` reference implementation already uses `findSnapshotByEmail`. An equivalent by-ID method may need to be added if it doesn't exist.
3. The `AccountJpaAdapter` already implements all other methods in `IAccountRepository` (domain port) except the new `findByAccountCodeWithUser`. Existing integration test confirms the adapter works.
