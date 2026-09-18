package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.util.UUID;

public record IngestionWork(
        UUID requestId,
        UUID targetId,
        JobSource source,
        String sourceAccount,
        String company,
        int attemptCount
) {
}
