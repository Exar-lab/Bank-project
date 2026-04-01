## java-code-review

Pre-merge code review checklist and testing patterns for all layers of banco-service: instant-reject conditions (Optional.get(), @Autowired fields, @Data DTOs, business logic in controllers, generic catch blocks), layer-specific test patterns (pure JUnit 5 for domain, Mockito for application, @WebMvcTest for presentation, Testcontainers for infrastructure), and coverage minimums per layer.

Use this skill when reviewing PRs, self-reviewing code, or writing tests for any layer.
