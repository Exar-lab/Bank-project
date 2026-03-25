CREATE TABLE IF NOT EXISTS outbox_events (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(100)   NOT NULL,
    aggregate_id   VARCHAR(255)   NOT NULL,
    event_type     VARCHAR(100)   NOT NULL,
    kafka_topic    VARCHAR(255)   NOT NULL,
    payload        TEXT           NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at     DATETIME(6)    NOT NULL,
    published_at   DATETIME(6)    NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
