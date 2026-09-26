ALTER TABLE ingestion_requests ADD COLUMN lease_owner UUID;

UPDATE ingestion_requests
   SET lease_owner = gen_random_uuid()
 WHERE status = 'RUNNING';
