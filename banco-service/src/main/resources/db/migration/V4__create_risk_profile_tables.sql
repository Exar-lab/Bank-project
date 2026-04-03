CREATE TABLE IF NOT EXISTS risk_profiles (
    id               BINARY(16)      NOT NULL,
    account_code     VARCHAR(64)     NOT NULL,
    risk_tier        VARCHAR(20)     NOT NULL,
    dynamic_score    DECIMAL(5,2)    NOT NULL,
    profile_version  BIGINT          NOT NULL,
    rule_set_version VARCHAR(32)     NOT NULL,
    created_at       DATETIME(6)     NOT NULL,
    updated_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_profiles_account_code (account_code),
    INDEX idx_risk_profiles_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS risk_profile_event_processing (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(128)    NOT NULL,
    consumer_name VARCHAR(100)    NOT NULL,
    processed_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_profile_event_consumer (event_id, consumer_name),
    INDEX idx_risk_profile_event_processed (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
