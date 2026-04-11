CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                 BINARY(16)      NOT NULL,
    user_id            BINARY(16)      NOT NULL,
    jti                VARCHAR(64)     NOT NULL,
    token_hash         CHAR(64)        NOT NULL,
    expires_at         DATETIME(6)     NOT NULL,
    created_at         DATETIME(6)     NOT NULL,
    revoked            TINYINT(1)      NOT NULL DEFAULT 0,
    revoked_at         DATETIME(6),
    revocation_reason  VARCHAR(30),
    replaced_by_jti    VARCHAR(64),
    parent_jti         VARCHAR(64),
    ip_address         VARCHAR(50),
    user_agent         VARCHAR(500),
    device_id          VARCHAR(100),

    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_jti UNIQUE (jti),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user_credential
        FOREIGN KEY (user_id) REFERENCES user_credentials(user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_user_id_active ON refresh_tokens (user_id, revoked);
CREATE INDEX idx_refresh_tokens_created_at ON refresh_tokens (created_at);
