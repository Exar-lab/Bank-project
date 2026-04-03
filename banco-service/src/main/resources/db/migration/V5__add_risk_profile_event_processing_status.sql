ALTER TABLE risk_profile_event_processing
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED' AFTER processed_at;
