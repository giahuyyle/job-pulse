package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.util.List;

public interface IngestionWriter {

    IngestionResult upsert(
            JobSource source,
            String sourceAccount,
            List<ExternalJob> jobs
    );
}
