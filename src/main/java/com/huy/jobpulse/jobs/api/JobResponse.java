package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.domain.JobPosting;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String company,
        String title,
        String location,
        String applyUrl,
        String status,
        Instant postedAt
) {
    public static JobResponse from(JobPosting job) {
        return new JobResponse(
                job.getId(),
                job.getCompany(),
                job.getTitle(),
                job.getLocation(),
                job.getApplyUrl(),
                job.getStatus().name(),
                job.getPostedAt()
        );
    }
}