CREATE TABLE job_postings (
    id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    source_account VARCHAR(160) NOT NULL,
    source_job_id VARCHAR(255) NOT NULL,

    company VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    location VARCHAR(500),
    description TEXT,
    employment_type VARCHAR(80),
    remote_policy VARCHAR(32) NOT NULL,

    apply_url TEXT NOT NULL,
    posted_at TIMESTAMPTZ,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,

    status VARCHAR(32) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_job_source_identity
      UNIQUE (source, source_account, source_job_id)
);

CREATE INDEX idx_job_postings_status_seen
    ON job_postings (status, last_seen_at DESC);

CREATE INDEX idx_job_postings_company
    ON job_postings (LOWER(company));

CREATE INDEX idx_job_postings_title
    ON job_postings (LOWER(title));