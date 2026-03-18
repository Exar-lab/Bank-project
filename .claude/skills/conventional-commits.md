---
name: conventional-commits
description: >
  Conventional commit format and PR title rules for banco-service. Scopes are feature or layer names.
  Trigger: When writing commit messages, PR titles, or reviewing git history.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Committing code changes
- Creating a PR title
- Reviewing a commit message for correctness
- Setting up git hooks or commit lint

---

## Critical Patterns

### Format

```
type(scope): subject

[optional body]

[optional footer]
```

- **type**: lowercase, from the list below
- **scope**: feature name OR layer name (see scopes section)
- **subject**: lowercase, imperative mood, no period at end, max 72 chars
- **NO AI attribution**: Never add `Co-Authored-By: AI` or any AI attribution line

### Types

| Type | Use For |
|---|---|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `docs` | Documentation only (`.md`, `.claude/`, `.atl/`) |
| `style` | Formatting, whitespace — no logic change |
| `refactor` | Code restructure without behavior change |
| `test` | Adding or fixing tests |
| `chore` | Build system, deps, config, CI — no production code |

### Scopes

Use either a **feature scope** (what business domain) or a **layer scope** (what technical layer):

**Feature scopes** (preferred when change is domain-specific):
```
account | transaction | envelope | user | card | role | permission
auditLog | fraud | security | exception | utils
```

**Layer scopes** (preferred when change spans multiple features or is purely technical):
```
domain | application | infrastructure | presentation
```

**Cross-cutting scopes**:
```
context | skills | deps | config | ci
```

---

## Examples

### Feature scopes
```bash
feat(account): add overdraft limit validation to deposit flow
fix(transaction): prevent duplicate transaction on concurrent requests
feat(card): implement card blocking with audit trail
refactor(envelope): extract allocation logic into EnvelopeAllocator
test(user): add unit tests for UserService password change
```

### Layer scopes
```bash
feat(presentation): add GlobalExceptionHandler with BankingException mapping
feat(infrastructure): configure Testcontainers for MySQL integration tests
refactor(domain): move balance validation from AccountService to Account entity
chore(deps): upgrade spring-boot to 4.0.2
```

### Documentation scopes
```bash
docs(context): rebuild .claude skills and context from actual codebase
docs(skills): add spring-security-jwt skill
docs(context): update roadmap with M1 controller milestones
```

### Multi-line commit with body
```bash
feat(security): implement JWT refresh token rotation

Access tokens expire in 15 minutes.
Refresh tokens expire in 30 days and are invalidated on use.
New refresh token is issued on each rotation.

Closes #42
```

---

## PR Title Format

Same as commit: `type(scope): description`

```
feat(account): REST controllers with RBAC and validation
fix(security): validate JWT algorithm to prevent none-alg attack
refactor(context): rename .gentle → .claude for provider specificity
test(account): domain and application layer test coverage
```

---

## What NOT to Do

```bash
# WRONG — no type/scope
git commit -m "Added account controller"

# WRONG — uppercase type
git commit -m "FEAT(account): add controller"

# WRONG — past tense subject
git commit -m "feat(account): added account controller"

# WRONG — period at end
git commit -m "feat(account): add account controller."

# WRONG — AI attribution
git commit -m "feat(account): add controller

Co-Authored-By: Claude <noreply@anthropic.com>"

# WRONG — scope is class name (too granular)
git commit -m "fix(AccountService): fix null pointer"

# WRONG — no scope
git commit -m "feat: add account controller"
```

---

## Git Workflow

```bash
# Stage specific files (preferred over `git add .`)
git add src/main/java/com/banco/co/account/service/AccountService.java
git add src/main/java/com/banco/co/account/controller/AccountController.java

# Commit
git commit -m "feat(account): add deposit and withdraw REST endpoints"

# PR: branch name matches scope
git checkout -b feat/account-rest-controllers
git checkout -b fix/transaction-duplicate-prevention
git checkout -b test/account-service-coverage
```

---

## Resources

- Conventional Commits spec: https://www.conventionalcommits.org/en/v1.0.0/
- `AGENTS.md` — Full project code review rules
- `.claude/context/project-roadmap.md` — Milestone context for PR descriptions
