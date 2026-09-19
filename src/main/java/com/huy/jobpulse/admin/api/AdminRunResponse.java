package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;
import com.huy.jobpulse.jobs.domain.JobSource;
import java.time.Instant;
import java.util.UUID;

public record AdminRunResponse(
        UUID id, JobSource source, String sourceAccount,
        IngestionRunStatus status, Instant startedAt, Instant completedAt,
        Integer discovered, Integer created, Integer updated,
        Integer unchanged, Integer closed, String failureMessage
) {
    public static AdminRunResponse from(IngestionRun run) {
        return new AdminRunResponse(run.getId(), run.getSource(),
                run.getSourceAccount(), run.getStatus(), run.getStartedAt(),
                run.getCompletedAt(), run.getDiscovered(), run.getCreated(),
                run.getUpdated(), run.getUnchanged(), run.getClosed(),
                run.getFailureMessage());
    }
}
