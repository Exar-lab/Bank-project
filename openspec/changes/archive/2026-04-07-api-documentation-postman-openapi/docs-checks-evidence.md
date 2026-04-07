# Docs Checks Evidence — api-documentation-postman-openapi

## Purpose
Provide deterministic, auditable evidence for documentation-only validation controls used to close verify warnings.

## Executed command

```bash
python -m unittest docs/verification/test_api_docs_consistency.py -v
```

## Expected deterministic outcomes
- `test_operation_ids_unique_and_complete` → pass (`66` operationIds, all unique)
- `test_openapi_postman_parity` → pass (`66` OpenAPI endpoints == `66` Postman requests after normalization)
- `test_mermaid_blocks_exist_for_required_diagrams` → pass (all required diagrams present and each has at least one Mermaid block)

## Mermaid source fingerprints (SHA-256)
- `docs/diagrams/account-lifecycle.md` → `5d2c27f8d4eb1f0becd6d26c7cde2f61bdd49469bc195ae35a01f09bf23e4699`
- `docs/diagrams/auth-flow.md` → `f1f68ab55989ac443133d45e46e13c59fdc50d5b4722b7be7057853260b5b3ca`
- `docs/diagrams/card-lifecycle.md` → `4a40be4c5d32610707a8e593d64e43a69f5813a62ff4bd1400f5aebafc4b28bb`
- `docs/diagrams/envelope-workflow.md` → `2a0fde2e1748140443ca39ae15cd9356ab9cee4cb589017022eeff4c8b8f0558`
- `docs/diagrams/transaction-transfer-flow.md` → `bc44b4be9866de76fa8d9e36162ad1bec4fa038a4acd1800339071bb7eb7bad2`

## Risk statement (REQ-4.S1)
GitHub runtime Mermaid rendering cannot be executed deterministically in this local environment because it depends on GitHub SaaS renderer behavior. This is accepted as non-blocking when deterministic local controls above remain green and fingerprints are tracked.
