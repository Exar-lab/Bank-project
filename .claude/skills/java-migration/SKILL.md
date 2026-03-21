## When to Use

- Creating a new database migration script for banco-service
- Adding a column, index, or constraint to an existing table
- Renaming or dropping a column (requires multi-step migration)
- Adding a new table for a new feature
- Troubleshooting Flyway validation errors

## Critical Patterns

### ✅ Correct Pattern — Migration File Naming

```
V1__init_schema.sql
V2__add_accounts_table.sql
V3__add_transactions_table.sql
V4__add_index_account_holder.sql
V5__add_outbox_events_table.sql
V6__add_card_table.sql
V7__add_column_account_currency.sql
```

Rules:
- Prefix: `V` (uppercase)
- Version: integer or decimal (e.g., `V1`, `V1.1`, `V2`)
- Separator: double underscore `__`
- Description: lowercase with underscores
- Extension: `.sql`
- NEVER modify an existing migration file. It will break Flyway checksum validation.

### ❌ Common Mistake — Modifying Existing Migration

```
# NEVER edit V3__add_transactions_table.sql after it has been applied
# Flyway stores a checksum — any change causes:
# FlywayException: Validate failed: Migration checksum mismatch...
```

**Fix**: Create a NEW migration file `V8__alter_transactions_add_column.sql`.

---

### ✅ Correct Pattern — New Table (MySQL)

```sql
-- V5__add_outbox_events_table.sql
CREATE TABLE outbox_events (
    id          CHAR(36)        NOT NULL                    COMMENT 'UUID primary key',
    aggregate_type VARCHAR(50)  NOT NULL                    COMMENT 'e.g. Transaction, Account',
    aggregate_id   CHAR(36)     NOT NULL                    COMMENT 'UUID of the aggregate',
    event_type     VARCHAR(100) NOT NULL                    COMMENT 'e.g. TransactionCreated',
    payload        TEXT         NOT NULL                    COMMENT 'JSON serialized event',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'  COMMENT 'PENDING | SENT | FAILED',
    created_at     DATETIME(6)  NOT NULL                    COMMENT 'Creation timestamp',
    sent_at        DATETIME(6)  NULL                        COMMENT 'When event was sent to Kafka',
    PRIMARY KEY (id),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id),
    INDEX idx_outbox_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### ❌ Common Mistake — Missing Engine and Charset

```sql
-- NEVER skip ENGINE and CHARSET
CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY
);
-- MySQL might use MyISAM (no transactions!) or wrong charset
```

**Why it matters**: Always specify `ENGINE=InnoDB` (required for foreign keys and transactions) and `DEFAULT CHARSET=utf8mb4` (supports emojis, special characters, full Unicode).

---

### ✅ Correct Pattern — Add Column (Safe)

```sql
-- V7__add_column_account_currency.sql
-- Safe: additive, existing rows get the default value
ALTER TABLE accounts
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'CRC' COMMENT 'ISO 4217 currency code';

-- Add an index if the column will be used in WHERE clauses
ALTER TABLE accounts
    ADD INDEX idx_accounts_currency (currency);
```

---

### ✅ Correct Pattern — Rename Column (2-Step Migration)

Never rename a column in a single migration if the application is running. Use the expand-contract pattern:

**Step 1** (V10__rename_account_holder_step1.sql):
```sql
-- Add new column with the new name
ALTER TABLE accounts
    ADD COLUMN holder_name VARCHAR(100) NULL COMMENT 'Replaces account_holder — step 1 of rename';

-- Copy existing data
UPDATE accounts SET holder_name = account_holder;

-- Make new column NOT NULL after data copy
ALTER TABLE accounts
    MODIFY COLUMN holder_name VARCHAR(100) NOT NULL;
```

**Step 2** (V11__rename_account_holder_step2.sql — run AFTER code is deployed):
```sql
-- Remove old column only after code no longer references it
ALTER TABLE accounts
    DROP COLUMN account_holder;
```

---

### ✅ Correct Pattern — Drop Column (3-Step)

1. V12: Mark column as deprecated in comments, ensure application no longer writes to it.
2. V13: Drop the column AFTER the code is deployed and verified not using it.
3. Never drop in one step without coordinating with code changes.

```sql
-- V13__drop_deprecated_account_holder.sql
ALTER TABLE accounts DROP COLUMN account_holder;
```

---

### ✅ Correct Pattern — Foreign Key with Index

```sql
-- V3__add_transactions_table.sql
CREATE TABLE transactions (
    id              CHAR(36)        NOT NULL,
    account_id      CHAR(36)        NOT NULL    COMMENT 'FK to accounts.id',
    amount          DECIMAL(19,4)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    type            VARCHAR(20)     NOT NULL     COMMENT 'CREDIT | DEBIT',
    status          VARCHAR(20)     NOT NULL     COMMENT 'PENDING | COMPLETED | FAILED',
    reference       VARCHAR(100)    NULL,
    created_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_transaction_account (account_id),
    INDEX idx_transaction_status (status),
    INDEX idx_transaction_created (created_at),
    CONSTRAINT fk_transaction_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Examples for Banco-Service

- `src/main/resources/db/migration/V1__init_schema.sql` — initial schema
- `src/main/resources/db/migration/V2__add_accounts_table.sql`
- `src/main/resources/db/migration/V3__add_transactions_table.sql`
- `src/main/resources/db/migration/V4__add_outbox_events_table.sql`

## Best Practices

- Migration files live in `src/main/resources/db/migration/`.
- NEVER modify an existing migration. Create a new one with the next version number.
- Always use `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` for all CREATE TABLE statements.
- Use `DECIMAL(19,4)` for monetary amounts — never FLOAT or DOUBLE.
- Use `CHAR(36)` for UUID primary keys stored as strings, or `BINARY(16)` for compact storage.
- Add `INDEX` for every foreign key column and every column used in a WHERE clause.
- Add `COMMENT` to every column — self-documenting schema matters for a banking system.
- For rename/drop operations, always use the expand-contract multi-step pattern.
- Test migrations locally with `./mvnw flyway:migrate` before committing.
- Flyway configuration is in `application.yml` under `spring.flyway.*`.
