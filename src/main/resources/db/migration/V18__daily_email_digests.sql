ALTER TABLE saved_searches
    ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_enabled_at TIMESTAMPTZ,
    ADD CONSTRAINT saved_search_email_opt_in CHECK (NOT email_enabled OR email_enabled_at IS NOT NULL);

CREATE TABLE email_settings (
    owner_subject VARCHAR(255) PRIMARY KEY,
    recipient VARCHAR(320) NOT NULL,
    next_digest_at TIMESTAMPTZ NOT NULL,
    last_accepted_at TIMESTAMPTZ,
    suppression VARCHAR(24),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX email_settings_due ON email_settings(next_digest_at);

CREATE TABLE email_digests (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL REFERENCES email_settings(owner_subject) ON DELETE CASCADE,
    schedule_date DATE NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    settings_version BIGINT NOT NULL,
    payload TEXT,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (state IN
        ('PENDING','SENDING','ACCEPTED','CANCELLED','DEFERRED','FAILED','UNCERTAIN')),
    provider_id VARCHAR(255) UNIQUE,
    delivery_status VARCHAR(24),
    attempts INTEGER NOT NULL DEFAULT 0,
    first_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_owner UUID,
    lease_expires_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    UNIQUE(owner_subject, schedule_date)
);
CREATE INDEX email_digests_due ON email_digests(next_attempt_at) WHERE state IN ('PENDING','SENDING');

CREATE TABLE email_candidates (
    alert_id UUID PRIMARY KEY REFERENCES job_alerts(id) ON DELETE CASCADE,
    owner_subject VARCHAR(255) NOT NULL REFERENCES email_settings(owner_subject) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'WAITING' CHECK (state IN ('WAITING','RESERVED','COVERED','CANCELLED','EXPIRED')),
    digest_id UUID REFERENCES email_digests(id) ON DELETE SET NULL
);
CREATE INDEX email_candidates_waiting ON email_candidates(owner_subject, created_at) WHERE state = 'WAITING';

CREATE TABLE email_covered_jobs (
    owner_subject VARCHAR(255) NOT NULL REFERENCES email_settings(owner_subject) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    digest_id UUID NOT NULL REFERENCES email_digests(id) ON DELETE CASCADE,
    PRIMARY KEY(owner_subject, job_id)
);
CREATE TABLE email_unsubscribe_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL REFERENCES email_settings(owner_subject) ON DELETE CASCADE,
    recipient VARCHAR(320) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE email_budget (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    blocked_until TIMESTAMPTZ
);
INSERT INTO email_budget(id) VALUES (1);
CREATE TABLE email_budget_reservations (
    digest_id UUID NOT NULL REFERENCES email_digests(id) ON DELETE CASCADE,
    budget_date DATE NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    released BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (digest_id, budget_date)
);
CREATE INDEX email_budget_time ON email_budget_reservations(reserved_at) WHERE NOT released;
CREATE TABLE email_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    provider_id VARCHAR(255) NOT NULL,
    type VARCHAR(80) NOT NULL,
    digest_id UUID REFERENCES email_digests(id) ON DELETE SET NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);
