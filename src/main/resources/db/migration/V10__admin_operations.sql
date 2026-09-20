ALTER TABLE ingestion_requests
    DROP CONSTRAINT ck_ingestion_request_status,
    ADD CONSTRAINT ck_ingestion_request_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    ADD COLUMN correlation_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE ingestion_runs
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN correlation_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE job_events
    ADD COLUMN publish_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_publish_error TEXT,
    ADD COLUMN last_publish_attempt_at TIMESTAMPTZ;

ALTER TABLE job_postings
    ADD COLUMN last_ingestion_run_id UUID,
    ADD COLUMN raw_payload TEXT,
    ADD CONSTRAINT fk_job_posting_last_run
        FOREIGN KEY (last_ingestion_run_id) REFERENCES ingestion_runs (id);

ALTER TABLE company_seeds
    ADD COLUMN reviewed_by VARCHAR(160),
    ADD COLUMN reviewed_at TIMESTAMPTZ;

CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY,
    actor VARCHAR(160) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    before_value TEXT,
    after_value TEXT,
    correlation_id UUID NOT NULL
);

CREATE INDEX idx_admin_audit_occurred ON admin_audit_log (occurred_at DESC);
CREATE INDEX idx_admin_audit_target ON admin_audit_log (target_type, target_id, occurred_at DESC);

CREATE TABLE ingestion_dead_letters (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    target_id UUID NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL,
    error_message TEXT NOT NULL,
    payload TEXT NOT NULL,
    replayed_at TIMESTAMPTZ,
    replay_request_id UUID,
    CONSTRAINT fk_dead_letter_request FOREIGN KEY (request_id) REFERENCES ingestion_requests (id),
    CONSTRAINT fk_dead_letter_target FOREIGN KEY (target_id) REFERENCES ingestion_targets (id)
);

CREATE INDEX idx_ingestion_dead_letters_open
    ON ingestion_dead_letters (failed_at DESC) WHERE replayed_at IS NULL;

CREATE INDEX idx_job_events_admin_filter
    ON job_events (event_type, published_at, created_at DESC);

CREATE INDEX idx_job_postings_admin
    ON job_postings (status, source, last_seen_at DESC);
