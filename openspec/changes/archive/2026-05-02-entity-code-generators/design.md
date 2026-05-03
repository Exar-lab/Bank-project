# Design: Entity-Owned Code Generation

## Decision

Use **feature-owned generator classes** and remove the current cross-feature central generator from business entity code.

This does not mean dumping random logic directly into entities. Entities should keep only lifecycle orchestration (`if null, assign generated value`). Format rules and random generation live in feature-local generator classes.

## Rationale

The central generator under `security` is a leaky abstraction. It has no real security responsibility and forces unrelated features to share generic methods. Because code formats are business rules, ownership should sit next to the feature that owns the entity.

This fits the project's feature-first hexagonal architecture better than a global utility.

## Target Structure

```text
com.banco.co.account.generator
  AccountCodeGenerator
  AccountNumberGenerator

com.banco.co.card.generator
  CardCodeGenerator
  CardNumberGenerator
  CardSecurityCodeGenerator

com.banco.co.envelope.generator
  EnvelopeCodeGenerator

com.banco.co.user.generator
  UserCodeGenerator

com.banco.co.transaction.generator
  TransactionCodeGenerator
```

## Format Preservation

- Account code: `CR-{currentYear}-{20 numeric digits}`
- Account number: `12 numeric digits`
- Card code: `CARD-{currentYear}-{6 safe alphanumeric chars}`
- Card CVV: `3 numeric digits`, or `4` for AMEX
- Envelope code: `ENV-{currentYear}-{10 safe alphanumeric chars}`
- User code: `USR-{currentYear}-{6 safe alphanumeric chars}`
- Username fallback: preserve current prefix/year/numeric style generated from first name unless tests reveal an existing expected contract that requires a different normalization.
- Transaction code: `TXN-{last 8 timestamp millis digits}-{8 numeric digits}`

Safe alphanumeric alphabet remains `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` to avoid confusing characters.

## Testing Strategy

Strict TDD is active.

Before production changes:

1. Add or update unit tests for each generator format.
2. Add or update entity tests for `@PrePersist` methods where practical.
3. Confirm generated values do not overwrite preassigned values.

Recommended test classes:

- `AccountCodeGeneratorTest`
- `AccountNumberGeneratorTest`
- `CardCodeGeneratorTest`
- `CardSecurityCodeGeneratorTest`
- `EnvelopeCodeGeneratorTest`
- `UserCodeGeneratorTest`
- `TransactionCodeGeneratorTest`
- Entity lifecycle tests in existing feature model test classes where present.

## Migration / Compatibility

No database migration is expected because existing column values and formats stay compatible. This is a code-architecture refactor.

## Risks and Mitigations

- **Collision risk**: Existing behavior already relies on random generation plus persistence constraints. This change preserves that behavior; repository-backed retry can be a separate future change.
- **Duplication risk**: Some random loops will repeat across feature generators. This is intentional until a stable neutral primitive is justified.
- **Package cleanup risk**: Removing `CodeGenerator` must happen only after all imports are replaced.

## Skill Resolution

Injected/found standards: spring-boot-patterns, jpa-patterns, design-patterns, clean-code, java-testing, java-fundamentals.
