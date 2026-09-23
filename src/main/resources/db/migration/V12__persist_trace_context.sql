ALTER TABLE ingestion_requests
    ADD COLUMN trace_parent VARCHAR(128),
    ADD COLUMN trace_state VARCHAR(1024),
    ADD COLUMN trace_baggage TEXT;

ALTER TABLE job_events
    ADD COLUMN trace_parent VARCHAR(128),
    ADD COLUMN trace_state VARCHAR(1024),
    ADD COLUMN trace_baggage TEXT;
