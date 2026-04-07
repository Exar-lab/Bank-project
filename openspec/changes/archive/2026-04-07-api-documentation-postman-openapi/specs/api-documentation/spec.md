# Delta Spec — api-documentation

## ADDED Requirements

### Requirement: REQ-1 OpenAPI Contract Coverage
The system MUST provide a hand-written OpenAPI 3.1 specification documenting all 66 REST endpoints, including security model, DTO/enum schemas, and sensitive-field redaction markers.

### Requirement: REQ-2 Postman Collection Coverage
The system MUST provide a Postman v2.1 collection with 66 requests aligned with OpenAPI endpoint definitions and standardized request-level test scripts.

### Requirement: REQ-3 Postman Environment Baseline
The system MUST provide a Postman environment with canonical variables (`baseUrl`, `bearerToken`, `accountId`, `cardCode`, `transactionId`, `envelopeCode`, `userId`).

### Requirement: REQ-4 Diagram Documentation
The system MUST provide Mermaid diagrams for auth and core business flows. REQ-4.S1 MAY remain partial when GitHub SaaS runtime rendering is not locally deterministic, provided deterministic controls remain green and documented.

### Requirement: REQ-5 Usage Snippets
The system MUST provide curl and JavaScript fetch snippets for account, card, transaction, envelope, and user flows using placeholders and no exposed credentials.

### Requirement: REQ-6 Coverage Report
The system MUST provide an endpoint coverage report proving 66/66 parity between OpenAPI and Postman artifacts.
