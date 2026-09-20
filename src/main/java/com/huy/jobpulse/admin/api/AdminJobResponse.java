package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminJobResponse(UUID id, JobSource source, String sourceAccount,
        String sourceJobId, String company, String title, String location,
        String description, String employmentType, String remotePolicy,
        String applyUrl, Instant postedAt, Instant firstSeenAt, Instant lastSeenAt,
        JobStatus status, String fingerprint, UUID lastIngestionRunId,
        String rawPayload, List<AdminEventResponse> events) {
    public static AdminJobResponse from(JobPosting job, List<AdminEventResponse> events) {
        return new AdminJobResponse(job.getId(), job.getSource(), job.getSourceAccount(),
                job.getSourceJobId(), job.getCompany(), job.getTitle(), job.getLocation(),
                job.getDescription(), job.getEmploymentType(), job.getRemotePolicy().name(),
                job.getApplyUrl(), job.getPostedAt(), job.getFirstSeenAt(), job.getLastSeenAt(),
                job.getStatus(), job.getContentHash(), job.getLastIngestionRunId(),
                job.getRawPayload(), events);
    }
}
