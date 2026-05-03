# Apply Progress: Entity Code Generators

## Status

Apply completed for the planned scope. Production refactor, generator tests, lifecycle tests, and targeted Maven verification passed with a portable Java 24 runtime.

## Completed

- [x] Added generator format tests:
  - `AccountCodeGeneratorTest`
  - `AccountNumberGeneratorTest`
  - `CardCodeGeneratorTest`
  - `CardSecurityCodeGeneratorTest`
  - `EnvelopeCodeGeneratorTest`
  - `UserCodeGeneratorTest`
  - `TransactionCodeGeneratorTest`
- [x] Added feature-owned generators:
  - `account/generator/AccountCodeGenerator`
  - `account/generator/AccountNumberGenerator`
  - `card/generator/CardCodeGenerator`
  - `card/generator/CardSecurityCodeGenerator`
  - `envelope/generator/EnvelopeCodeGenerator`
- [x] Updated existing feature generators:
  - `user/generator/UserCodeGenerator` now owns username fallback generation.
  - `transaction/generator/TransactionCodeGenerator` no longer delegates to the central generator.
- [x] Updated entities to use feature-owned generators:
  - `Account`
  - `Card`
  - `Envelope`
  - `User`
- [x] Deleted misplaced central generator:
  - `security/codeGenerator/CodeGenerator.java`
- [x] Confirmed no production imports remain for `security.codeGenerator.CodeGenerator`.
- [x] Added entity lifecycle tests after pre-verify review flagged the gap:
  - `AccountLifecycleTest`
  - `CardLifecycleTest`
  - `EnvelopeLifecycleTest`
  - `UserLifecycleTest`
  - `TransactionLifecycleTest`
- [x] Updated `tasks.md` checklist to reflect completed implementation work.
- [x] Fixed re-verify findings:
  - `CardLifecycleTest` now sets `CardTier.CLASSIC` before invoking `generateCardData()`.
  - Added `CardNumberGeneratorTest` to cover Luhn-valid Visa and Amex numbers.
  - Removed `ReflectionTestUtils` usage from Account and Transaction lifecycle tests.
- [x] Fixed targeted test failure discovered after Java 24 was configured:
  - `EnvelopeLifecycleTest` now sets `EnvelopeType.SAVINGS` before invoking `generateEnvelopeData()` because envelope default icon/color depend on type.

## Verification

Initial targeted command attempted from `banco-service/` before Java 24 was configured:

```powershell
.\mvnw.cmd "-Dtest=AccountCodeGeneratorTest,AccountNumberGeneratorTest,CardCodeGeneratorTest,CardSecurityCodeGeneratorTest,EnvelopeCodeGeneratorTest,UserCodeGeneratorTest,TransactionCodeGeneratorTest" test
```

Result:

```text
Fatal error compiling: error: release version 24 not supported
```

The initial targeted test run did not execute because the active local JDK did not support Java release 24.

Environment evidence:

```text
java -version -> 21.0.8
JAVA_HOME -> C:\Program Files\Java\jdk-21
java executable -> C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
installed under C:\Program Files\Java -> jdk-21, jre1.8.0_491, latest
```

Portable Java 24 configured for the command session:

```text
JAVA_HOME -> C:\Users\Exar1\AppData\Local\Temp\opencode\jdk24\jdk-24.0.2+12
java -version -> openjdk version "24.0.2" Temurin-24.0.2+12
```

Final targeted test command from `banco-service/`:

```powershell
.\mvnw.cmd "-Dtest=AccountCodeGeneratorTest,AccountNumberGeneratorTest,AccountLifecycleTest,CardCodeGeneratorTest,CardSecurityCodeGeneratorTest,CardNumberGeneratorTest,CardLifecycleTest,EnvelopeCodeGeneratorTest,EnvelopeLifecycleTest,UserCodeGeneratorTest,UserLifecycleTest,TransactionCodeGeneratorTest,TransactionLifecycleTest" test
```

Final result:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Work

- [x] Install or point `JAVA_HOME` to a Java 24-compatible JDK, then run targeted generator/model lifecycle tests.
- [x] Add direct entity lifecycle callback tests for generated-field preservation where feasible.
- [x] Add/restore CardNumberGenerator Luhn tests.
- [x] Run `/sdd-verify entity-code-generators` after tests can execute. Final behavioral verification passed, then Strict TDD audit requested the evidence table below.

## TDD Cycle Evidence

| Cycle | Phase | Evidence | Result |
|-------|-------|----------|--------|
| 1 | RED | Added generator format tests before creating feature-owned generators: `AccountCodeGeneratorTest`, `AccountNumberGeneratorTest`, `CardCodeGeneratorTest`, `CardSecurityCodeGeneratorTest`, `EnvelopeCodeGeneratorTest`, `UserCodeGeneratorTest`, `TransactionCodeGeneratorTest`. These referenced generators/methods that did not exist yet or still depended on the central generator. | Expected failing/compile-red state before production implementation. |
| 1 | GREEN | Added feature-owned generators for account/card/envelope and updated user/transaction generators to own their random/format logic. Updated Account/Card/Envelope/User entities to call feature-owned generators and deleted `security/codeGenerator/CodeGenerator.java`. | Generator format tests became implementable against feature-owned classes. |
| 2 | RED | Multi-agent verify flagged missing lifecycle coverage for `@PrePersist` null fallback generation and preassigned-value preservation. Added `AccountLifecycleTest`, `CardLifecycleTest`, `EnvelopeLifecycleTest`, `UserLifecycleTest`, and `TransactionLifecycleTest`. | New lifecycle tests covered spec scenarios that were previously untested. |
| 2 | GREEN | Fixed lifecycle setup requirements discovered by review/runtime: `CardLifecycleTest` sets `CardTier.CLASSIC`; `EnvelopeLifecycleTest` sets `EnvelopeType.SAVINGS`; Account/Transaction preservation tests avoid `ReflectionTestUtils`. | Lifecycle tests aligned with domain invariants and pure domain test standards. |
| 3 | TRIANGULATE | Added `CardNumberGeneratorTest` for both Visa and AMEX to prove Luhn validity, not just code-format generation. | Covered card-number domain behavior independently from card-code generation. |
| 4 | SAFETY NET | Ran targeted Maven tests with portable Java 24 from `banco-service/`: `.\mvnw.cmd "-Dtest=AccountCodeGeneratorTest,AccountNumberGeneratorTest,AccountLifecycleTest,CardCodeGeneratorTest,CardSecurityCodeGeneratorTest,CardNumberGeneratorTest,CardLifecycleTest,EnvelopeCodeGeneratorTest,EnvelopeLifecycleTest,UserCodeGeneratorTest,UserLifecycleTest,TransactionCodeGeneratorTest,TransactionLifecycleTest" test`. | `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`. |

## Notes

No full build was run. Only targeted Maven tests were executed. The initial Java 21 failure was resolved by using a portable Java 24 runtime for the final targeted test command.
