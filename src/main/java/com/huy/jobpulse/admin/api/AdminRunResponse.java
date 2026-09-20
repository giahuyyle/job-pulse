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
        Integer unchanged, Integer closed, String failureMessage,
        long durationMs, double throughputPerSecond, int retryCount,
        UUID correlationId
) {
    public static AdminRunResponse from(IngestionRun run) {
        return from(run, run.getRetryCount());
    }

    public static AdminRunResponse from(IngestionRun run, int retryCount) {
        return new AdminRunResponse(run.getId(), run.getSource(),
                run.getSourceAccount(), run.getStatus(), run.getStartedAt(),
                run.getCompletedAt(), run.getDiscovered(), run.getCreated(),
                run.getUpdated(), run.getUnchanged(), run.getClosed(),
                run.getFailureMessage(), durationMs(run), throughput(run),
                retryCount, run.getCorrelationId());
    }

    private static long durationMs(IngestionRun run) {
        Instant end = run.getCompletedAt() == null ? Instant.now() : run.getCompletedAt();
        return Math.max(0, java.time.Duration.between(run.getStartedAt(), end).toMillis());
    }

    private static double throughput(IngestionRun run) {
        if (run.getDiscovered() == null) return 0;
        long millis = durationMs(run);
        return millis == 0 ? run.getDiscovered() : run.getDiscovered() / (millis / 1000.0);
    }
}
