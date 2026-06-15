# Apply Progress — hexagonal-arch-phase-3-account

**Mode**: Strict TDD (compilation gate + unit test gate)  
**Last updated**: 2026-06-14  
**Branch**: refactor/hexagonal-arch-phase-3-transaction-to-master

---

## Phase 1: Foundation — COMPLETED ✅

### TDD Evidence Table

| Task | RED | GREEN | Commit |
|------|-----|-------|--------|
| 1.1 Add `findSnapshotByUserId` to `IUserRepository` + `UserJpaAdapter` | `UserRepositorySnapshotTest` created (compile-fail until method added) | Method added to port + adapter; 355 tests pass | `00492d0` |
| 1.2 Add `findByAccountCodeWithUser` to domain `IAccountRepository` + `AccountJpaAdapter` | `AccountJpaAdapterIntegrationTest#testFindByAccountCodeWithUser_ExistingAccount_PopulatesUserId` added | Port + JPA repo query + adapter implemented | `8d0cb01` |
| 1.3 Extend `IAccountUseCase` with 5 domain-typed methods | `AccountServiceDomainMethodsTest` (compile-fail until 5 methods added) | Methods added with `default` stubs; conflict resolved by injecting domain port into `AccountService` and renaming legacy `IAccountService` methods | `da78a28` + `eff49fa` |
| 1.4 Add `accountId` UUID field to `Card` | `CardServiceTest#testCreateCard_ValidData_CardHasAccountId` (compile-fail until field added) | `@Column(name="account_id", insertable=false, updatable=false)` added to `Card.java` | `fc422d5` |

### Test Run Result

```
Tests run: 355, Failures: 0, Errors: 0, Skipped: 25
BUILD SUCCESS
Total time: 35.803 s
```

(25 skipped = Testcontainers/Docker integration tests — Docker not available in CI)

---

## Files Created / Modified

### New files
- `banco-service/src/test/java/com/banco/co/user/adapter/out/jpa/UserRepositorySnapshotTest.java`
- `banco-service/src/test/java/com/banco/co/account/service/AccountServiceDomainMethodsTest.java`

### Modified files
- `banco-service/src/main/java/com/banco/co/user/domain/port/out/IUserRepository.java` — added `findSnapshotByUserId(UUID)`
- `banco-service/src/main/java/com/banco/co/user/adapter/out/jpa/UserJpaAdapter.java` — implemented `findSnapshotByUserId`
- `banco-service/src/main/java/com/banco/co/account/domain/port/out/IAccountRepository.java` — added `findByAccountCodeWithUser(String)`
- `banco-service/src/main/java/com/banco/co/account/adapter/out/jpa/IAccountJpaRepository.java` — added `findAccountWithUser` JPQL query
- `banco-service/src/main/java/com/banco/co/account/adapter/out/jpa/AccountJpaAdapter.java` — implemented `findByAccountCodeWithUser`
- `banco-service/src/main/java/com/banco/co/account/domain/port/in/IAccountUseCase.java` — added 5 domain-typed default methods
- `banco-service/src/main/java/com/banco/co/account/service/IAccountService.java` — renamed `getAccountById` → `getAccountEntityById`, `findAccountWithUserByAccountCode` → `findAccountEntityByCode` (avoid return-type clash)
- `banco-service/src/main/java/com/banco/co/account/service/AccountService.java` — injected domain `IAccountRepository` port; added domain-typed `@Override` implementations for all 5 `IAccountUseCase` methods; renamed legacy method impls
- `banco-service/src/main/java/com/banco/co/card/model/Card.java` — added `accountId` UUID field with `insertable=false, updatable=false`
- `banco-service/src/main/java/com/banco/co/transaction/service/TransactionService.java` — updated to use renamed legacy methods
- `banco-service/src/main/java/com/banco/co/card/service/CardService.java` — updated to use renamed legacy methods
- `banco-service/src/main/java/com/banco/co/envelope/service/EnvelopeService.java` — updated to use renamed legacy methods
- `banco-service/src/test/java/com/banco/co/account/adapter/out/jpa/AccountJpaAdapterIntegrationTest.java` — added Task 1.2 RED test
- `banco-service/src/test/java/com/banco/co/card/service/CardServiceTest.java` — added Task 1.4 RED test; updated method names
- `banco-service/src/test/java/com/banco/co/transaction/service/TransactionServiceFraudGateTest.java` — updated method names
- `banco-service/src/test/java/com/banco/co/transaction/service/TransactionServiceNotificationTest.java` — updated method names

---

## Notable Design Decisions Made

### Task 1.3 conflict resolution
`IAccountService` had `getAccountById(UUID)` returning legacy `Account` and `findAccountWithUserByAccountCode(String)` returning legacy `Account`. Adding the same methods to `IAccountUseCase` returning domain `Account` caused a Java return-type clash in `AccountService` (which implements both).

**Resolution**: renamed the conflicting `IAccountService` methods to `getAccountEntityById` and `findAccountEntityByCode` (marked `@Deprecated`). Updated all 3 callers (TransactionService, EnvelopeService, CardService) and their tests. Added domain port (`com.banco.co.account.domain.port.out.IAccountRepository`) injection to `AccountService` and implemented the 5 `IAccountUseCase` domain-typed methods using the domain port.

---

## Next Phase

**Phase 2** — Rewrite `AccountService` to implement only `IAccountUseCase` and inject domain ports exclusively (Tasks 2.1.x). This removes `IAccountService` implementation from `AccountService` and migrates all persistence calls through `AccountJpaAdapter`. The legacy `IAccountService` methods can be cleaned up once Phase 3 callers are migrated.
