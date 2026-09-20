package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminJobSummaryResponse(UUID id, String title, String company,
        JobSource source, String sourceAccount, String sourceJobId, JobStatus status,
        Instant lastSeenAt, UUID lastIngestionRunId) {
    public static AdminJobSummaryResponse from(JobPosting job) {
        return new AdminJobSummaryResponse(job.getId(), job.getTitle(), job.getCompany(),
                job.getSource(), job.getSourceAccount(), job.getSourceJobId(), job.getStatus(),
                job.getLastSeenAt(), job.getLastIngestionRunId());
    }
}
