package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.RemotePolicy;

import java.time.Instant;

public record ExternalJob(
        String sourceJobId,
        String company,
        String title,
        String location,
        String description,
        String employmentType,
        RemotePolicy remotePolicy,
        String applyUrl,
        Instant postedAt
) {
}
