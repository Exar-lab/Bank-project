# Proposal: Entity-Owned Code Generators

## Intent

Rebuild entity code generation so each feature/entity owns its identifier format instead of relying on `security.codeGenerator.CodeGenerator` as a cross-feature central utility.

## Why

The central generator currently mixes unrelated formats behind generic methods. This hides business rules like prefixes, length, allowed alphabet, numeric-only constraints, timestamp usage, and card-specific validation. It also creates an incorrect dependency from business features into the security package.

## Scope

In scope:

- Create or complete feature-owned generators for Account, Card, Envelope, User, and Transaction code generation.
- Replace entity imports/usages of `com.banco.co.security.codeGenerator.CodeGenerator`.
- Preserve current externally visible formats unless a spec explicitly changes them.
- Add focused unit tests for generator format rules and entity `@PrePersist` behavior.
- Remove the central `security/codeGenerator/CodeGenerator.java` when no longer used.

Out of scope:

- Changing database schemas or existing persisted codes.
- Adding repository-backed collision retry logic.
- Changing card number generation behavior beyond keeping existing Luhn behavior.
- Running a full build; project instruction forbids builds after changes.

## Approach

Use feature-owned static generator classes with explicit method names and constants:

- `account/generator/AccountCodeGenerator`
- `account/generator/AccountNumberGenerator` or combined account generator if cleaner
- `card/generator/CardCodeGenerator`
- `card/generator/CardSecurityCodeGenerator`
- existing `card/generator/CardNumberGenerator`
- `envelope/generator/EnvelopeCodeGenerator`
- existing `user/generator/UserCodeGenerator` updated to own all user-related generation it needs
- existing `transaction/generator/TransactionCodeGenerator` updated to remove central dependency

## Rollback Plan

Revert the generator classes and entity imports to previous `CodeGenerator` calls. No data migration should be required because generated code formats remain compatible.

## Success Criteria

- No production class imports `com.banco.co.security.codeGenerator.CodeGenerator`.
- Each entity delegates code generation to its own feature package.
- Tests prove the current formats and entity fallback behavior.
- SDD apply stops using the central security generator for business identifiers.

## Skill Resolution

Injected/found standards: spring-boot-patterns, jpa-patterns, design-patterns, clean-code, java-testing, java-fundamentals.
