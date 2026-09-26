ALTER TABLE job_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN publish_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN lease_owner UUID,
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

UPDATE job_events SET publish_status = 'PUBLISHED' WHERE published_at IS NOT NULL;

ALTER TABLE job_events ADD CONSTRAINT ck_job_event_publish_status
    CHECK (publish_status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED'));

CREATE INDEX idx_job_events_due_publication
    ON job_events (next_attempt_at, created_at)
    WHERE publish_status = 'PENDING';

CREATE INDEX idx_job_events_expired_lease
    ON job_events (lease_expires_at)
    WHERE publish_status = 'PUBLISHING';
