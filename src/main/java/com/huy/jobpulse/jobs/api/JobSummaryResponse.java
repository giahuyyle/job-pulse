package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.application.JobSearchHit;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;

import java.time.Instant;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String company,
        String location,
        RemotePolicy remotePolicy,
        JobSource source,
        Instant postedAt,
        Instant firstSeenAt,
        String applyUrl
) {
    public static JobSummaryResponse from(JobSearchHit hit) {
        return new JobSummaryResponse(
                hit.id(),
                hit.title(),
                hit.company(),
                hit.location(),
                hit.remotePolicy(),
                hit.source(),
                hit.postedAt(),
                hit.firstSeenAt(),
                hit.applyUrl()
        );
    }

    public static JobSummaryResponse from(JobPosting job) {
        return new JobSummaryResponse(
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getRemotePolicy(),
                job.getSource(),
                job.getPostedAt(),
                job.getFirstSeenAt(),
                job.getApplyUrl()
        );
    }
}
