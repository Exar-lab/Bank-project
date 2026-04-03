## api-contract-review

REST API contract standards for banco-service: correct HTTP verb/status code mapping (GET→200, POST→201, DELETE→204), mandatory @Valid on all @RequestBody parameters, URL versioning under /api/v1/, GlobalExceptionHandler with no stack traces in responses, @PreAuthorize on every non-public endpoint, and structured ErrorResponseDto for all error cases.

Use this skill before designing or reviewing any REST controller, exception handler, or request DTO.
