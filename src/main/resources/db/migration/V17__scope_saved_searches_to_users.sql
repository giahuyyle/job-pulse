ALTER TABLE saved_searches
    ADD COLUMN owner_subject VARCHAR(255) NOT NULL DEFAULT 'legacy-local';

ALTER TABLE saved_searches
    ALTER COLUMN owner_subject DROP DEFAULT;

UPDATE saved_searches SET enabled = FALSE WHERE owner_subject = 'legacy-local';

CREATE INDEX idx_saved_searches_owner_created
    ON saved_searches (owner_subject, created_at DESC, id DESC);
