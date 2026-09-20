package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;
import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.jobs.domain.JobSource;
import java.time.Instant;
import java.util.UUID;

public record AdminBoardResponse(UUID id, String company, JobSource provider,
        String sourceAccount, boolean enabled, int pollingIntervalMinutes,
        Instant lastSuccessfulRunAt, IngestionRunStatus lastRunStatus) {
    public static AdminBoardResponse from(IngestionTarget target, IngestionRun latest) {
        return new AdminBoardResponse(target.getId(), target.getCompany(), target.getSource(),
                target.getSourceAccount(), target.isEnabled(), target.getIntervalMinutes(),
                target.getLastSuccessAt(), latest == null ? null : latest.getStatus());
    }
}
