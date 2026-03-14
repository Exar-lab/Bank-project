---
name: conventional-commits
description: >
  Follow Conventional Commits format (feat, fix, refactor, test, docs).
  Trigger: When writing commit messages or creating PRs.
license: Apache-2.0
metadata:
  author: gentleman-architecture
  version: "1.0"
  tags: [git, commits, documentation]
---

## Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

## Types

| Type | Use Case |
|------|----------|
| **feat** | New feature |
| **fix** | Bug fix |
| **refactor** | Code refactor (no behavior change) |
| **perf** | Performance improvement |
| **test** | Add/update tests |
| **docs** | Documentation changes |
| **style** | Code formatting |
| **chore** | Dependencies, config |

## Scope (Optional)

Affected component:

```
feat(domain): add Account withdraw method
feat(application): create TransferUseCase
feat(infrastructure): implement KafkaEventPublisher
feat(presentation): add AccountController
```

## Subject Rules

- ✅ Imperative mood: "add", "fix", "refactor"
- ✅ Lowercase first letter
- ✅ No period at end
- ✅ Max 50 characters
- ❌ Don't use "fixed", "added", "changed"

## Examples

```bash
# ✅ CORRECT
git commit -m "feat(domain): add Account withdraw with validation"
git commit -m "fix(transfer): prevent overdraft in transfer logic"
git commit -m "test(accounts): add unit tests for Account model"
git commit -m "refactor(repository): simplify AccountRepositoryImpl"
git commit -m "perf(persistence): add database index on holder column"

# ❌ WRONG
git commit -m "update stuff"
git commit -m "fixed account bug"
git commit -m "URGENT FIX!!!"
git commit -m "code changes"
```

## With Body

```bash
git commit -m "feat(domain): add Account withdrawal method

- Validate balance before withdrawal
- Return new Account instance (immutability)
- Throw InsufficientFundsException if balance < amount

Closes #42"
```

## Breaking Changes

```bash
git commit -m "refactor(domain): change Account.balance type

BREAKING CHANGE: Account.balance() now returns BigDecimal
Migration: Use BigDecimal.setScale(2, RoundingMode.HALF_UP)"
```

## Tools

Check commits before pushing:

```bash
# Install commitlint
npm install -g @commitlint/cli

# Validate message
echo "feat: test" | commitlint
```

## PR Title Format

```
feat(feature-name): Brief description of the feature
fix(bug-area): Brief description of the fix
```

Example:
```
feat(accounts): implement account creation with balance validation
fix(transfer): fix concurrent transfer race condition
```
