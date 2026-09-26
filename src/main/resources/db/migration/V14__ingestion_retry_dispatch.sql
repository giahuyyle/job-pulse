ALTER TABLE ingestion_requests
    ADD COLUMN dispatch_after TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_ingestion_requests_due_dispatch
    ON ingestion_requests (dispatch_after, created_at)
    WHERE published_at IS NULL AND status = 'PENDING';
