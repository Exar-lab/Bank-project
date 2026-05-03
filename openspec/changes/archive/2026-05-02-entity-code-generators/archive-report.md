# Archive Report: Entity Code Generators

## Status

Archived successfully.

## Change

`entity-code-generators`

## Summary

Rebuilt business code generation from a misplaced central `security.codeGenerator.CodeGenerator` utility into feature-owned generators for Account, Card, Envelope, User, and Transaction.

## Final Implementation

- Feature-owned generators added or updated:
  - `account/generator/AccountCodeGenerator`
  - `account/generator/AccountNumberGenerator`
  - `card/generator/CardCodeGenerator`
  - `card/generator/CardSecurityCodeGenerator`
  - `card/generator/CardNumberGenerator`
  - `envelope/generator/EnvelopeCodeGenerator`
  - `user/generator/UserCodeGenerator`
  - `transaction/generator/TransactionCodeGenerator`
- Entity lifecycle callbacks now use feature-owned generators.
- Removed central generator:
  - `banco-service/src/main/java/com/banco/co/security/codeGenerator/CodeGenerator.java`
- No production dependency remains on `com.banco.co.security.codeGenerator.CodeGenerator`.

## Verification

- Strict TDD audit: PASSED.
- Tasks: 25/25 complete.
- Targeted Maven tests with portable Java 24:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

No full build was run, respecting project instruction.

## Artifact Sources

- `exploration.md`
- `proposal.md`
- `specs/entity-code-generation/spec.md`
- `design.md`
- `tasks.md`
- `apply-progress.md`

## Engram Topics

- `sdd/entity-code-generators/explore`
- `sdd/entity-code-generators/proposal`
- `sdd/entity-code-generators/spec`
- `sdd/entity-code-generators/design`
- `sdd/entity-code-generators/tasks`
- `sdd/entity-code-generators/apply-progress`
- `sdd/entity-code-generators/verify-report`
- `sdd/entity-code-generators/archive-report`

## Risks

None open for the archived scope.

Future consideration: if collision handling becomes a real production concern, design a repository-backed retry allocator as a separate SDD change.
