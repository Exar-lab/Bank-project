# Exploration: Entity Code Generators

## Context

The project currently mixes feature-owned code generators with a centralized generator located under `com.banco.co.security.codeGenerator`.

Current findings:

- `security/codeGenerator/CodeGenerator.java` is used by Account, Card, Envelope, User, and Transaction-adjacent generators.
- `user/generator/UserCodeGenerator.java` owns user code generation but still `User` uses the central `CodeGenerator` for username generation.
- `transaction/generator/TransactionCodeGenerator.java` owns transaction code format but delegates numeric random generation to the central `CodeGenerator`.
- `card/generator/CardNumberGenerator.java` is already feature-owned and contains card-specific Luhn logic.
- `Account`, `Card`, `Envelope`, `User`, and `Transaction` generate codes from `@PrePersist` callbacks.

## Architectural Problem

`security.codeGenerator.CodeGenerator` is not actually a security concern. It is a generic utility imported across business features. That creates the wrong dependency direction: account/card/envelope/user/transaction depend on a security package for domain identifiers.

The user's concern is valid: each entity may need different prefixes, lengths, alphabets, numeric-only formats, timestamp formats, or domain constraints. A central generator tends to become a parameter soup.

## Recommendation

Do **feature-owned generators**, not a full central domain generator.

Concretely:

- Keep each feature's public generator in its own package, e.g. `account/generator/AccountCodeGenerator`.
- Move entity-specific format rules into constants inside that feature generator.
- Remove cross-feature usage of `security.codeGenerator.CodeGenerator`.
- Keep `CardNumberGenerator` as a card-specific generator because Luhn and brand prefixes are card-domain rules.
- Prefer explicit generator methods over generic `generateWithPrefix(String, int)` calls inside entities.

## Decision Tradeoff

Small duplication of low-level random loops is acceptable here because it protects feature autonomy and avoids a misleading central abstraction. If later several generators need identical hardened behavior, extract a neutral internal primitive only after the pattern stabilizes.

## Affected Code

- `banco-service/src/main/java/com/banco/co/security/codeGenerator/CodeGenerator.java`
- `banco-service/src/main/java/com/banco/co/account/model/Account.java`
- `banco-service/src/main/java/com/banco/co/card/model/Card.java`
- `banco-service/src/main/java/com/banco/co/envelope/model/Envelope.java`
- `banco-service/src/main/java/com/banco/co/user/model/User.java`
- `banco-service/src/main/java/com/banco/co/transaction/model/Transaction.java`
- Existing/target generator packages under each feature.

## Risks

- Static random generators are easy to test for format but not deterministic unless tests validate patterns instead of exact values.
- Collision handling remains DB-constraint-driven unless a later change adds repository-backed retry allocation.
- Removing the central generator may break imports across multiple entities if not done atomically.

## Skill Resolution

Injected/found standards: spring-boot-patterns, jpa-patterns, design-patterns, clean-code, java-testing, java-fundamentals.
