# SDD Proposal: api-documentation-postman-openapi

## Intent
Create a complete, hand-crafted API documentation suite for banco-service without changing Java code or build dependencies.

## Scope
- OpenAPI 3.1 specification
- Postman collection and environment
- Mermaid diagrams for key flows
- Usage snippets
- Coverage report with endpoint parity

## Constraints
- Documentation-only change (no Java source edits)
- No `pom.xml` dependency additions
- Deterministic local validation required

## Success Criteria
- 66 endpoints documented in both OpenAPI and Postman
- Sensitive fields remain redacted
- Verification gate reaches PASS WITH WARNINGS or better with no criticals

## Risks
- GitHub Mermaid runtime rendering cannot be validated deterministically locally.
- Accepted as non-blocking only with deterministic controls and evidence.

## Source Traceability
- Recovered from Engram artifact topic `sdd/api-documentation-postman-openapi/proposal` (observation #374).
