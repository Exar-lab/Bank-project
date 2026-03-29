# Skill: release-please

## When to Use

- Creating a GitHub release for the project
- Preparing or bumping to a new version
- Updating CHANGELOG.md
- Any question about versioning, release automation, or what commit triggers which version bump
- Reviewing or merging a release PR opened by release-please
- Debugging why a commit didn't show up in the changelog

---

## Critical Patterns

### ✅ Correct — Conventional commit that triggers a release bump

```
feat(account): add balance freeze endpoint
^    ^          ^
|    |          └─ description
|    └─ scope (optional but recommended)
└─ type → determines version bump
```

**Bump rules**:

| Commit type | Version bump | Example |
|-------------|-------------|---------|
| `fix:` | patch (0.0.X) | `fix(transaction): prevent duplicate debit on retry` |
| `feat:` | minor (0.X.0) | `feat(envelope): add envelope transfer use case` |
| `feat!:` or `BREAKING CHANGE:` footer | major (X.0.0) | `feat!: remove legacy card endpoint` |
| `perf:`, `refactor:`, `chore:`, `docs:` | patch (0.0.X) | `refactor(account): extract balance validation to domain method` |

### ❌ Wrong — commit that release-please ignores (won't appear in changelog)

```
# Missing type prefix entirely
added balance freeze endpoint

# Wrong format — slash instead of colon
feat/account: add something

# Vague message with no type
update service

# Type without colon
feat account add something
```

### ✅ Correct — Two-step release workflow

```
Step 1: Push conventional commits to master
         ↓
         release-please reads commit history
         ↓
         Opens PR: "chore(master): release 0.1.0"
         (updates pom.xml version + CHANGELOG.md)

Step 2: Developer reviews the release PR and merges it
         ↓
         release-please creates GitHub Release + git tag v0.1.0
```

The PR is NOT a code PR. It only updates version and changelog.
You review it to confirm the changelog is accurate, then merge.

### ❌ Wrong — manually bumping the version

```xml
<!-- NEVER do this manually in pom.xml -->
<version>0.2.0</version>
<!-- release-please owns this — manual edits conflict with the manifest -->
```

### ✅ Correct — breaking change syntax

```
feat!: redesign account creation API

BREAKING CHANGE: CreateAccountRequest now requires accountType field.
Clients must update their request payload.
```

Both `feat!:` in the subject line and `BREAKING CHANGE:` in the footer trigger a major bump. Use both for clarity.

---

## Examples for Banco-Service

### Commits that trigger a **minor** bump (new feature)

```
feat(account): add account freeze/unfreeze endpoints
feat(transaction): implement scheduled transaction support
feat(envelope): add multi-envelope transfer use case
feat(card): add virtual card generation endpoint
feat(fraud): add real-time transaction scoring via ML adapter
```

### Commits that trigger a **patch** bump (fix / perf / refactor / docs)

```
fix(transaction): correct double-debit on concurrent withdraw requests
fix(account): handle null balance in AccountNotFoundException message
perf(account): replace N+1 query in findAllWithTransactionCount
refactor(envelope): extract budget validation to EnvelopePolicy domain method
docs(transaction): add OpenAPI description to TransactionController endpoints
chore(security): rotate JWT signing key configuration
```

### Commits that trigger a **major** bump (breaking change)

```
feat!: migrate account API from v1 to v2 — remove deprecated endpoints
fix!: change TransactionResponse.amount from String to BigDecimal

# Or with footer:
feat(account): redesign account creation flow

BREAKING CHANGE: POST /accounts now requires `accountType` enum field.
```

### Commits that appear in changelog but don't bump (chore, docs without !)

```
chore(ci): update GitHub Actions runner to ubuntu-22.04
docs: add architecture decision record for Outbox pattern
chore(deps): bump spring-boot-starter-parent to 3.3.1
```

---

## Workflow

### Full release cycle

```
1. Developer merges feature/fix branch into master with conventional commits
   └─ Example: feat(account): add balance freeze endpoint

2. release-please GitHub Action runs on push to master
   └─ Reads all unreleased commits since last tag
   └─ Calculates next version (patch / minor / major)
   └─ Opens or updates a PR titled: "chore(master): release 0.2.0"
      └─ PR diff: pom.xml version bump + CHANGELOG.md new section

3. Developer reviews the release PR
   └─ Check: changelog sections look correct?
   └─ Check: version bump is appropriate?
   └─ If yes → merge the PR

4. Post-merge: release-please automatically
   └─ Creates GitHub Release (with CHANGELOG section as body)
   └─ Creates git tag: v0.2.0
   └─ Updates .release-please-manifest.json with new version
```

### What the release PR looks like

```markdown
## chore(main): release 0.2.0

### Features
- **account**: add balance freeze endpoint (#42)
- **envelope**: add multi-envelope transfer use case (#44)

### Bug Fixes
- **transaction**: correct double-debit on concurrent withdraw (#43)
```

The PR body is auto-generated from commit history. Do NOT manually edit it.

---

## Config Files

Two files at the git root control release-please behavior:

### `release-please-config.json`
Defines the Maven package path and which commit types appear in the changelog:

```json
{
  "packages": {
    "banco-service": {
      "release-type": "maven",
      "changelog-sections": [
        { "type": "feat",     "section": "Features" },
        { "type": "fix",      "section": "Bug Fixes" },
        { "type": "perf",     "section": "Performance Improvements" },
        { "type": "refactor", "section": "Refactoring" },
        { "type": "chore",    "section": "Miscellaneous" },
        { "type": "docs",     "section": "Documentation" }
      ]
    }
  }
}
```

The key `"banco-service"` matches the subfolder where `pom.xml` lives.

### `.release-please-manifest.json`
Tracks the current released version for each package:

```json
{
  "banco-service": "0.0.1"
}
```

Release-please updates this file automatically on every release. Never edit it manually.

---

## Never

- **Never manually edit the release PR** — release-please manages it. Manual edits may cause conflicts on the next run.
- **Never manually bump `<version>` in pom.xml** — release-please owns that field. Manual edits conflict with the manifest and break the automation.
- **Never use non-conventional commits for changes that should appear in the changelog** — if a commit doesn't start with `feat:`, `fix:`, `perf:`, etc., it will be invisible to release-please.
- **Never delete or manually edit `.release-please-manifest.json`** — this is release-please's state file. Corrupting it causes incorrect version calculations.
- **Never force-push to master after a release tag** — tags are the source of truth for release-please; rewriting history after a tag breaks the diff range for the next release.
