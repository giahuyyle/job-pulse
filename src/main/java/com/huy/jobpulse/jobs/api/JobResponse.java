package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String company,
        String title,
        String location,
        String description,
        String employmentType,
        RemotePolicy remotePolicy,
        JobSource source,
        String applyUrl,
        String status,
        Instant postedAt,
        Instant firstSeenAt
) {
    public static JobResponse from(JobPosting job) {
        return new JobResponse(
                job.getId(),
                job.getCompany(),
                job.getTitle(),
                job.getLocation(),
                job.getDescription(),
                job.getEmploymentType(),
                job.getRemotePolicy(),
                job.getSource(),
                job.getApplyUrl(),
                job.getStatus().name(),
                job.getPostedAt(),
                job.getFirstSeenAt()
        );
    }
}
