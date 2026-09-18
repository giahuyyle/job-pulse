package com.huy.jobpulse.jobs.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;

import java.time.Instant;
import java.util.UUID;

public record JobSearchHit(
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
}
