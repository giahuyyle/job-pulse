CREATE TABLE ingestion_runs (
    id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    source_account VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    discovered INTEGER,
    created INTEGER,
    updated INTEGER,
    unchanged INTEGER,
    closed INTEGER,
    failure_message TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ingestion_runs_source_started
    ON ingestion_runs (source, source_account, started_at DESC);
