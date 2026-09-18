ALTER TABLE job_events
    DROP CONSTRAINT ck_job_event_type,
    DROP CONSTRAINT uk_job_event;

ALTER TABLE job_events
    ADD CONSTRAINT ck_job_event_type
        CHECK (event_type IN ('CREATED', 'UPDATED', 'CLOSED')),
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN source VARCHAR(32),
    ADD COLUMN source_account VARCHAR(160),
    ADD COLUMN company VARCHAR(255),
    ADD COLUMN title VARCHAR(500),
    ADD COLUMN remote_policy VARCHAR(32),
    ADD COLUMN published_at TIMESTAMPTZ;

UPDATE job_events event
   SET source = posting.source,
       source_account = posting.source_account,
       company = posting.company,
       title = posting.title,
       remote_policy = posting.remote_policy
  FROM job_postings posting
 WHERE posting.id = event.job_posting_id;

ALTER TABLE job_events
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN source_account SET NOT NULL,
    ALTER COLUMN company SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN remote_policy SET NOT NULL;

CREATE UNIQUE INDEX uk_job_events_created
    ON job_events (job_posting_id)
    WHERE event_type = 'CREATED';

CREATE INDEX idx_job_events_unpublished
    ON job_events (created_at, id)
    WHERE published_at IS NULL;

CREATE TABLE job_event_consumptions (
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE daily_job_stats (
    day DATE NOT NULL,
    source VARCHAR(32) NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (day, source, event_type),
    CONSTRAINT ck_daily_job_stats_event_type
        CHECK (event_type IN ('CREATED', 'CLOSED')),
    CONSTRAINT ck_daily_job_stats_count CHECK (count >= 0)
);
