## jpa-patterns

JPA entity design, Spring Data repository patterns, and N+1 prevention for banco-service: @Entity without @Data, JOIN FETCH / DTO projection queries to eliminate N+1, @Transactional(readOnly=true) for all reads, proper @Transactional for multi-step writes, and the Outbox pattern for reliable Kafka event publishing within a single database transaction.

Use this skill before writing or modifying any @Entity class, repository interface, or service method with @Transactional.
