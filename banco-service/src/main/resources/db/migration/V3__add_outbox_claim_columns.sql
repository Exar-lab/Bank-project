ALTER TABLE outbox_events
    ADD COLUMN claimed_by VARCHAR(100) NULL AFTER published_at,
    ADD COLUMN claimed_at DATETIME(6) NULL AFTER claimed_by;

CREATE INDEX idx_outbox_claimed_by_status ON outbox_events (claimed_by, status);
