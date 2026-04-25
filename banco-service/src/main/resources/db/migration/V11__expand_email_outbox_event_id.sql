ALTER TABLE email_outbox_events
    MODIFY COLUMN event_id VARCHAR(100) NOT NULL;
