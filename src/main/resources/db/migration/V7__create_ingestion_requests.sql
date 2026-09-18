CREATE TABLE ingestion_requests (
    id UUID PRIMARY KEY,
    ingestion_target_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    lease_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_ingestion_request_target
        FOREIGN KEY (ingestion_target_id)
        REFERENCES ingestion_targets (id),
    CONSTRAINT ck_ingestion_request_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ingestion_request_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_ingestion_requests_unpublished
    ON ingestion_requests (created_at)
    WHERE published_at IS NULL AND status = 'PENDING';

CREATE INDEX idx_ingestion_requests_expired_lease
    ON ingestion_requests (lease_until)
    WHERE status = 'RUNNING';

CREATE UNIQUE INDEX uk_ingestion_requests_outstanding_target
    ON ingestion_requests (ingestion_target_id)
    WHERE status IN ('PENDING', 'RUNNING');
