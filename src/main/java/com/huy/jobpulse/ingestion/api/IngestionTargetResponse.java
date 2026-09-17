package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.jobs.domain.JobSource;

import java.time.Instant;
import java.util.UUID;

public record IngestionTargetResponse(
        UUID id,
        JobSource source,
        String sourceAccount,
        String company,
        String careersUrl,
        boolean enabled,
        int intervalMinutes,
        Instant nextRunAt,
        Instant lastSuccessAt,
        String lastError
) {
    public static IngestionTargetResponse from(IngestionTarget target) {
        return new IngestionTargetResponse(
                target.getId(),
                target.getSource(),
                target.getSourceAccount(),
                target.getCompany(),
                target.getCareersUrl(),
                target.isEnabled(),
                target.getIntervalMinutes(),
                target.getNextRunAt(),
                target.getLastSuccessAt(),
                target.getLastError()
        );
    }
}
