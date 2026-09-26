ALTER TABLE ingestion_dead_letters
    ADD COLUMN replayed_by VARCHAR(160),
    ADD COLUMN replay_reason TEXT;
