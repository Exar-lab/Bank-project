CREATE TABLE IF NOT EXISTS cards (
    id                        BINARY(16)      NOT NULL,
    card_code                 VARCHAR(20)     NOT NULL,
    card_number               VARCHAR(255)    NOT NULL,
    security_code             VARCHAR(255)    NOT NULL,
    pin_hash                  VARCHAR(60)     NOT NULL,
    card_type                 VARCHAR(20)     NOT NULL,
    brand                     VARCHAR(20),
    tier                      VARCHAR(20),
    account_id                BINARY(16)      NOT NULL,
    status                    VARCHAR(20)     NOT NULL DEFAULT 'INACTIVE',
    blocked_reason            VARCHAR(500),
    created_at                DATETIME(6)     NOT NULL,
    expiration_date           DATETIME(6)     NOT NULL,
    activated_at              DATETIME(6),
    last_used_at              DATETIME(6),
    updated_at                DATETIME(6)     NOT NULL,
    daily_limit               DECIMAL(19,2)   NOT NULL,
    monthly_limit             DECIMAL(19,2)   NOT NULL,
    daily_spent               DECIMAL(19,2)   NOT NULL DEFAULT 0.00,
    monthly_spent             DECIMAL(19,2)   NOT NULL DEFAULT 0.00,
    contactless_enabled       TINYINT(1)      NOT NULL DEFAULT 1,
    online_payments_enabled   TINYINT(1)      NOT NULL DEFAULT 1,
    international_enabled     TINYINT(1)      NOT NULL DEFAULT 0,
    points                    BIGINT          NOT NULL DEFAULT 0,
    last_transaction_at       DATETIME(6),
    last_transaction_amount   DECIMAL(19,2),
    last_transaction_type     VARCHAR(50),

    PRIMARY KEY (id),
    CONSTRAINT uq_cards_card_code   UNIQUE (card_code),
    CONSTRAINT uq_cards_card_number UNIQUE (card_number),
    CONSTRAINT fk_cards_account
        FOREIGN KEY (account_id) REFERENCES account(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cards_account_id     ON cards (account_id);
CREATE INDEX idx_cards_status         ON cards (status);
CREATE INDEX idx_cards_account_status ON cards (account_id, status);
