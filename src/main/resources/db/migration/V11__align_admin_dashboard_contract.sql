ALTER TABLE ingestion_requests
    ADD COLUMN retry_of_run_id UUID,
    ADD CONSTRAINT fk_ingestion_request_retry_run
        FOREIGN KEY (retry_of_run_id) REFERENCES ingestion_runs (id);

ALTER TABLE job_events
    ADD COLUMN retry_requested_at TIMESTAMPTZ;

CREATE INDEX idx_ingestion_requests_retry_run
    ON ingestion_requests (retry_of_run_id)
    WHERE retry_of_run_id IS NOT NULL;
