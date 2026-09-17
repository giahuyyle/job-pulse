CREATE TABLE ingestion_targets (
    id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    source_account VARCHAR(160) NOT NULL,
    company VARCHAR(255) NOT NULL,
    careers_url TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    interval_minutes INTEGER NOT NULL DEFAULT 60,
    next_run_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_ingestion_target
        UNIQUE (source, source_account),
    CONSTRAINT ck_ingestion_interval
        CHECK (interval_minutes BETWEEN 15 AND 1440)
);

CREATE INDEX idx_ingestion_targets_due
    ON ingestion_targets (next_run_at)
    WHERE enabled = TRUE;
