# Tasks — hexagonal-arch-phase-3-transaction

## Phase 1: Domain foundation

- [x] 1.1 Create `transaction/domain/model/Transaction.java` as pure POJO/domain model with UUID cross-feature references and business methods preserved.
- [x] 1.2 Create `transaction/domain/port/in/ITransactionUseCase.java` with legacy service method signatures.
- [x] 1.3 Create `transaction/domain/port/out/ITransactionRepository.java` as clean output port, no JPA/Spring Data repository inheritance.

## Phase 2: JPA adapter scaffolding

- [x] 2.1 Create `transaction/adapter/out/jpa/TransactionEntity.java` as anemic JPA entity mapped to `transactions`.
- [x] 2.2 Create `transaction/adapter/out/jpa/ITransactionJpaRepository.java` for Spring Data persistence.
- [x] 2.3 Create `transaction/adapter/out/jpa/TransactionEntityMapper.java` for MapStruct entity/domain mapping.
- [x] 2.4 Create `transaction/adapter/out/jpa/TransactionJpaAdapter.java` implementing the output port.

## Phase 3: Service compatibility

- [x] 3.1 Update `transaction/service/TransactionService.java` to implement `ITransactionUseCase` alongside `ITransactionService`.

## Phase 4: REST adapter copies

- [x] 4.1 Create `transaction/adapter/in/rest/TransactionController.java` using `ITransactionUseCase`; keep `@RestController` inactive.
- [x] 4.2 Create `transaction/adapter/in/rest/TransactionAdminController.java`; keep `@RestController` inactive.
- [x] 4.3 Create `transaction/adapter/in/rest/TransactionEmployeeController.java`; keep `@RestController` inactive.
- [x] 4.4 Create `transaction/adapter/in/rest/TransactionMetadataExtractor.java` package copy.

## Phase 5: Internal consumer import updates

- [x] 5.1 Update `transaction/mapper/ITransactionMapper.java` to domain `Transaction`.
- [x] 5.2 Update `transaction/utils/metadata/ITransactionMetadataEnricher.java` to domain `Transaction`.
- [x] 5.3 Update `transaction/utils/metadata/TransactionMetadataEnricher.java` to domain `Transaction`.

Completed in contract phase commit `fa4e64d` alongside the `TransactionService` rewrite.

## Phase 6: Legacy cleanup

- [x] 6.1 Delete legacy `transaction/model/Transaction.java`.
- [x] 6.2 Delete legacy `transaction/repository/ITransactionRepository.java`.
- [x] 6.3 Delete legacy `transaction/controller/` — adapter controllers activated.
- [x] 6.4 Delete legacy `transaction/service/ITransactionService.java`.

Deferred to the follow-up service rewrite/contraction slice.

## Review workload

- `size:exception`: approved by user on 2026-05-28.
- Reason: current additive slice is ~3.1k inserted LOC, above the 400-line review budget, but was already applied in a previous SDD session and has passing tests. Follow-up rewrite MUST be separate.
