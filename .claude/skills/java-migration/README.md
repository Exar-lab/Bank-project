## java-migration

Flyway database migration conventions for banco-service on MySQL: `V{n}__{description}.sql` naming, additive-only changes (never modify existing migrations), MySQL-specific table creation (ENGINE=InnoDB, utf8mb4, DECIMAL(19,4) for money), expand-contract patterns for rename/drop operations, and proper index strategy for banking queries.

Use this skill before writing any SQL migration file in `src/main/resources/db/migration/`.
