CREATE TABLE company_seeds (
    id UUID PRIMARY KEY,
    company_name TEXT NOT NULL,
    careers_url TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'PENDING',
    last_checked_at TIMESTAMPTZ,
    matched_url TEXT,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_company_seeds_status
    ON company_seeds (status);
