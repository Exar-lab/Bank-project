# Apply Progress — api-documentation-postman-openapi

## Mode
- Hybrid artifact mode (Engram + filesystem)
- Strict TDD enabled (test runner available), docs-only implementation scope preserved

## Objective for this continuation
Close all verify warnings before archive with minimal, auditable, deterministic changes.

## Changes implemented

1. Added deterministic executable docs checks:
   - `docs/verification/test_api_docs_consistency.py`
   - Verifies OpenAPI operation count + uniqueness (66), OpenAPI/Postman parity (66/66), and Mermaid block presence in required diagram set.

2. Updated design artifact to remove drift:
   - `openspec/changes/api-documentation-postman-openapi/design.md`
   - Corrected endpoint total to **66**.
   - Corrected schema naming strategy to **preserve `*Dto` suffix** (matches `docs/api/openapi.yaml`).
   - Added explicit docs-validation strategy (deterministic checks + accepted GitHub-render runtime risk).

3. Added deterministic evidence artifact:
   - `openspec/changes/api-documentation-postman-openapi/docs-checks-evidence.md`
   - Captures exact command and stable expected outcomes.
   - Includes SHA-256 fingerprints of Mermaid diagram source files for audit traceability.

## Strict TDD cycle evidence (classic, executable)

| Task | Test File | Layer | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|
| Close warning #2 (adapted strict-TDD evidence) | `docs/verification/test_api_docs_consistency.py` | Unit (Python unittest for docs artifacts) | ✅ Initial run failed (`test_openapi_postman_parity`) due path-variable normalization order | ✅ `python -m unittest docs/verification/test_api_docs_consistency.py -v` passed (3/3) after fix | ✅ Three independent checks (OpenAPI IDs, parity, Mermaid blocks) | ✅ Normalization order adjusted to correctly map `{{var}}` and `{var}` |

## Warning resolution status

### W1 — REQ-4.S1 partial (GitHub Mermaid runtime renderer not executed)
- **Status**: Converted to explicit, accepted non-blocking risk (environmental constraint).
- **Rationale**: GitHub renderer is SaaS runtime behavior and cannot be deterministically executed offline in this local environment.
- **Prevent-recurrence controls**:
  - Required Mermaid blocks enforced by executable test (`test_mermaid_blocks_exist_for_required_diagrams`).
  - Diagram source SHA-256 fingerprints recorded in `docs-checks-evidence.md`.
  - Design now documents this as accepted risk with deterministic local controls.

### W2 — Strict-TDD evidence adapted (docs-only)
- **Status**: Closed.
- **Fix**: Added executable strict-TDD style test file and recorded real RED→GREEN cycle evidence in this artifact.

### W3 — Design drift (64 endpoints / old schema naming)
- **Status**: Closed.
- **Fix**: Updated design artifact to `66` endpoints and `*Dto` preserved naming policy.

## Deterministic checks run
- Command: `python -m unittest docs/verification/test_api_docs_consistency.py -v`
- Result: `OK (3 tests)`

## Scope guardrails honored
- No Java source files modified
- No full build executed
- Documentation-only scope preserved
