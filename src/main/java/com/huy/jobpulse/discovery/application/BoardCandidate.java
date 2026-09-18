package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.net.URI;

public record BoardCandidate(
        JobSource source,
        String sourceAccount,
        URI matchedUrl
) {
}
