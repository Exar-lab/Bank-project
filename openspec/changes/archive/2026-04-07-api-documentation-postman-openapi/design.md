# SDD Design — api-documentation-postman-openapi (updated)

## Scope
Documentation-only change set for API contract artifacts (OpenAPI, Postman, diagrams, snippets, coverage report) with deterministic local validation.

## Canonical decisions (current)

### D1. Endpoint baseline
- Canonical total endpoints: **66**
- Source of truth: `docs/api/openapi.yaml` (`operationId` count) and Postman collection request count parity.

### D2. Schema naming convention
- OpenAPI schema names MUST preserve Java record suffix `*Dto`.
- Rationale: deterministic 1:1 mapping with Java DTO records and reduced ambiguity in coverage matrix.
- Marker: `info.x-schema-naming-convention` in `docs/api/openapi.yaml`.

### D3. Documentation validation strategy (Strict-TDD compatible)
- For docs-only changes, strict-TDD evidence SHALL be executable at artifact-validation layer.
- Deterministic checks implemented as tests in:
  - `docs/verification/test_api_docs_consistency.py`
- Enforced checks:
  1. OpenAPI `operationId` total/uniqueness (66, unique)
  2. OpenAPI ↔ Postman endpoint parity (66/66 after path normalization)
  3. Mermaid block presence in required diagram inventory

### D4. Mermaid rendering risk model
- Requirement REQ-4.S1 depends partly on GitHub runtime Mermaid renderer (SaaS runtime).
- Local environment cannot deterministically execute GitHub renderer.
- Accepted non-blocking risk with compensating controls:
  - Mermaid source syntax presence checks via executable tests
  - Stable source fingerprints (SHA-256) recorded in docs evidence artifact

## Evidence artifacts
- `openspec/changes/api-documentation-postman-openapi/apply-progress.md`
- `openspec/changes/api-documentation-postman-openapi/docs-checks-evidence.md`
- `docs/verification/test_api_docs_consistency.py`

## Out-of-scope
- Java source changes
- Full project build execution
- CI pipeline changes (recommended for future hardening)
