package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.discovery.domain.CompanySeed;
import com.huy.jobpulse.discovery.domain.CompanySeedStatus;

import java.util.UUID;

public record DiscoverySeedOutcome(
        UUID seedId,
        String companyName,
        String careersUrl,
        CompanySeedStatus status,
        String matchedUrl,
        String error
) {

    public static DiscoverySeedOutcome from(CompanySeed seed) {
        return new DiscoverySeedOutcome(
                seed.getId(),
                seed.getCompanyName(),
                seed.getCareersUrl(),
                seed.getStatus(),
                seed.getMatchedUrl(),
                seed.getLastError()
        );
    }
}
