# Design — hexagonal-arch-phase-3-transaction

## Architecture decision

This slice uses the hexagonal package convention:

- Domain behavior and ports: `transaction/domain/**`
- Inbound REST adapters: `transaction/adapter/in/rest/**`
- Outbound JPA adapters/entities/repositories: `transaction/adapter/out/jpa/**`
- Transitional application service remains at `transaction/service/TransactionService.java` for compatibility with prior phases.

## Entity placement decision

JPA entities for migrated slices belong to infrastructure persistence adapters, not domain packages.

`TransactionEntity` therefore lives at:

```text
banco-service/src/main/java/com/banco/co/transaction/adapter/out/jpa/TransactionEntity.java
```

This updates the old project rule that allowed entities under `{feature}/model/`. After hexagonal migration, `{feature}/model/` is legacy only; new/migrated entities live under `{feature}/adapter/out/jpa/`, while pure domain models live under `{feature}/domain/model/`.

## Additive migration shape

The current slice is intentionally additive:

1. Create domain model and ports.
2. Create JPA adapter scaffolding.
3. Add `ITransactionUseCase` compatibility to `TransactionService`.
4. Copy REST adapters into the new package with controllers inactive to avoid duplicate Spring mappings.
5. Defer the full service rewrite and legacy deletions to a follow-up slice.

## Deferred blocker

`TransactionService` still imports and uses:

- `com.banco.co.transaction.model.Transaction`
- `com.banco.co.transaction.repository.ITransactionRepository`
- `com.banco.co.transaction.service.ITransactionService`

Because of that, these are intentionally deferred:

- `transaction/mapper/ITransactionMapper.java` domain import swap
- `transaction/utils/metadata/ITransactionMetadataEnricher.java` domain import swap
- `transaction/utils/metadata/TransactionMetadataEnricher.java` domain import swap
- deletion of legacy `transaction/model/`, `transaction/repository/`, `transaction/controller/`, and `ITransactionService`

## Follow-up slice

The next implementation slice should rewrite `TransactionService` to use the new domain model and output port end-to-end, then activate REST adapters and delete legacy packages.
