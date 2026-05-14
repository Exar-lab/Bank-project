# Tasks: Entity-Owned Code Generators

## 1. Tests First

- [x] 1.1 Add generator format tests for Account code and account number.
- [x] 1.2 Add generator format tests for Card code and CVV/security code.
- [x] 1.3 Add/keep tests for Card number Luhn behavior.
- [x] 1.4 Add generator format tests for Envelope code.
- [x] 1.5 Add/update generator format tests for User code and username fallback.
- [x] 1.6 Add/update generator format tests for Transaction code.
- [x] 1.7 Add entity lifecycle tests proving null generated fields are filled and preassigned values are preserved.

## 2. Implement Feature-Owned Generators

- [x] 2.1 Create `account/generator/AccountCodeGenerator`.
- [x] 2.2 Create `account/generator/AccountNumberGenerator` or a cohesive account generator if cleaner.
- [x] 2.3 Create `card/generator/CardCodeGenerator`.
- [x] 2.4 Create `card/generator/CardSecurityCodeGenerator`.
- [x] 2.5 Create `envelope/generator/EnvelopeCodeGenerator`.
- [x] 2.6 Update `user/generator/UserCodeGenerator` so user generation does not call the central generator.
- [x] 2.7 Update `transaction/generator/TransactionCodeGenerator` so transaction generation does not call the central generator.

## 3. Replace Entity Usage

- [x] 3.1 Update `Account` to use account feature generators.
- [x] 3.2 Update `Card` to use card feature generators.
- [x] 3.3 Update `Envelope` to use envelope feature generator.
- [x] 3.4 Update `User` to use only user feature generator methods.
- [x] 3.5 Confirm `Transaction` already uses transaction generator and no longer depends indirectly on central generator.

## 4. Remove Central Generator

- [x] 4.1 Search production code for `security.codeGenerator.CodeGenerator` imports.
- [x] 4.2 Delete `com.banco.co.security.codeGenerator.CodeGenerator` only after no production usage remains.
- [x] 4.3 Remove unused imports and unused random fields.

## 5. Verify

- [x] 5.1 Run targeted Maven tests from `banco-service/` for modified generator/model tests. Passed with portable JDK 24: 22 tests, 0 failures, 0 errors, 0 skipped.
- [x] 5.2 Do not run a full build because project instructions forbid builds after changes.
- [x] 5.3 Save apply progress in `sdd/entity-code-generators/apply-progress` during implementation.

## Stop Point

Planning stops here because the user requested automatic SDD only until apply.
