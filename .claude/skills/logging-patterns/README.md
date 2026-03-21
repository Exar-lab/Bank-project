## logging-patterns

SLF4J + Logback structured logging conventions for banco-service: parameterized log calls (never string concatenation), correct log level selection (DEBUG/INFO/WARN/ERROR), MDC for request correlation IDs, Kafka event logging (before publish + after consume), and a hard ban on logging sensitive data (passwords, JWT tokens, card PANs).

Use this skill before adding any log statement to services, controllers, Kafka publishers, or security filters.
