ALTER TABLE job_postings
    ADD COLUMN consecutive_missing_runs INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN closed_at TIMESTAMPTZ;
