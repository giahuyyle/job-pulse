package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

public interface IngestionCoordinator {

    IngestionResult ingest(JobSource source, String sourceAccount);
}
