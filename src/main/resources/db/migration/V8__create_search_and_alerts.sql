ALTER TABLE job_postings
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B')
    ) STORED;

CREATE INDEX idx_job_postings_search_vector
    ON job_postings USING GIN (search_vector);

CREATE INDEX idx_job_postings_active_first_seen
    ON job_postings (first_seen_at DESC, id DESC)
    WHERE status = 'ACTIVE';

CREATE TABLE saved_searches (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    query VARCHAR(150),
    company VARCHAR(255),
    source VARCHAR(32),
    remote_policy VARCHAR(32),
    location VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE job_events (
    id UUID PRIMARY KEY,
    job_posting_id UUID NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,

    CONSTRAINT fk_job_event_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
    CONSTRAINT ck_job_event_type CHECK (event_type IN ('CREATED')),
    CONSTRAINT uk_job_event UNIQUE (job_posting_id, event_type)
);

CREATE INDEX idx_job_events_unprocessed
    ON job_events (created_at, id)
    WHERE processed_at IS NULL;

CREATE TABLE job_alerts (
    id UUID PRIMARY KEY,
    saved_search_id UUID NOT NULL,
    job_posting_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_job_alert_search
        FOREIGN KEY (saved_search_id) REFERENCES saved_searches (id),
    CONSTRAINT fk_job_alert_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
    CONSTRAINT uk_job_alert UNIQUE (saved_search_id, job_posting_id)
);

CREATE INDEX idx_job_alerts_unread_created
    ON job_alerts (created_at DESC, id DESC)
    WHERE read_at IS NULL;
