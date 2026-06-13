# Proposal — hexagonal-arch-phase-3-transaction

## Intent

Refactor the `transaction` feature from the legacy flat layered layout into the same additive hexagonal / ports-and-adapters structure already used by the migrated `user` and `account` slices.

## Scope

### In

- Add pure domain model: `transaction/domain/model/Transaction.java`.
- Add input port: `transaction/domain/port/in/ITransactionUseCase.java`.
- Add output port: `transaction/domain/port/out/ITransactionRepository.java`.
- Add JPA adapter stack under `transaction/adapter/out/jpa/`:
  - `TransactionEntity`
  - `ITransactionJpaRepository`
  - `TransactionEntityMapper`
  - `TransactionJpaAdapter`
- Add inactive REST adapter copies under `transaction/adapter/in/rest/` while legacy controllers remain active.
- Keep `TransactionService` implementing both legacy `ITransactionService` and new `ITransactionUseCase` during the additive phase.

### Out

- No endpoint contract changes.
- No DTO changes.
- No Flyway/schema migration.
- No full `TransactionService` domain-port rewrite in this slice.
- No deletion of legacy transaction packages in this slice.

## Review workload decision

`size:exception` is approved by the user for this additive slice.

Reason: the slice was already mostly applied from the previous SDD session, tests are green, and the immediate next safe step is to restore artifacts and verify/archive the additive state rather than mixing in a larger service rewrite.

## Key decision

Hexagonal architecture supersedes the old AGENTS rule that entities must live under `{feature}/model/`. For migrated slices, JPA entities live in infrastructure persistence adapters such as `{feature}/adapter/out/jpa/`, while pure domain models live under `{feature}/domain/model/`.
