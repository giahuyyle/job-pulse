package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.util.List;
import java.util.UUID;

public interface IngestionWriter {

    IngestionResult apply(
            UUID runId,
            JobSource source,
            String sourceAccount,
            List<ExternalJob> jobs
    );
}
