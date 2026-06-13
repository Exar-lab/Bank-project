# Spec — hexagonal-arch-phase-3-transaction

## Requirement: Pure transaction domain model

The system MUST introduce `transaction/domain/model/Transaction.java` as a pure Java domain model with no JPA, Hibernate, or Spring Data imports.

### Scenario: Domain model has no persistence dependency

- GIVEN `transaction/domain/model/Transaction.java`
- WHEN imports are inspected
- THEN no import SHALL contain `jakarta.persistence`, `org.hibernate`, or `org.springframework.data`.

### Scenario: Business behavior is preserved

- GIVEN a domain transaction in a valid state
- WHEN domain methods such as `process`, `complete`, `fail`, `reverse`, `flagForFraud`, `approve`, or `canBeReversed` are called
- THEN the same business transitions and exceptions as the legacy model SHALL be preserved.

## Requirement: Input use-case port

The system MUST introduce `transaction/domain/port/in/ITransactionUseCase.java` with the legacy service contract and no persistence imports.

### Scenario: TransactionService implements additive compatibility

- GIVEN the additive migration phase
- WHEN `TransactionService` is compiled
- THEN it SHALL implement both `ITransactionService` and `ITransactionUseCase`.

## Requirement: Output repository port

The system MUST introduce `transaction/domain/port/out/ITransactionRepository.java` as a plain port returning domain `Transaction` objects and not extending Spring Data repositories.

### Scenario: Output port is persistence-free

- GIVEN the output port
- WHEN imports and annotations are inspected
- THEN no `JpaRepository`, `@Query`, `@Param`, `@EntityGraph`, or JPA annotation SHALL appear.

## Requirement: JPA adapter

The system MUST introduce the transaction persistence implementation under `transaction/adapter/out/jpa/`.

### Scenario: JPA entity lives in infrastructure adapter

- GIVEN the hexagonal migration policy
- WHEN `TransactionEntity` is inspected
- THEN it SHALL live under `transaction/adapter/out/jpa/`, map to the same `transactions` table, and contain persistence mapping only.

### Scenario: Adapter implements output port

- GIVEN `TransactionJpaAdapter`
- WHEN its class declaration is inspected
- THEN it SHALL implement `transaction.domain.port.out.ITransactionRepository`.

## Requirement: REST adapter copies

The system MUST introduce REST adapter copies under `transaction/adapter/in/rest/` using `ITransactionUseCase`.

### Scenario: Additive adapters avoid duplicate mappings

- GIVEN legacy controllers still exist
- WHEN new REST adapter copies are present
- THEN their `@RestController` mapping SHALL remain inactive until the legacy controllers are removed.

## Requirement: Deferred legacy contraction

The system MUST NOT delete legacy transaction model/repository/controller/service interface until the active service is rewritten to use the domain model and output port end-to-end.

### Scenario: Deferred tasks remain explicit

- GIVEN the current additive slice
- WHEN Phase 5 import updates and Phase 6 deletions are evaluated
- THEN they SHALL be marked deferred and moved into a follow-up service rewrite/contraction slice.
