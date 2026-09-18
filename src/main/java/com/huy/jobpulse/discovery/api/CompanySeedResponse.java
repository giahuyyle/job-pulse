package com.huy.jobpulse.discovery.api;

import com.huy.jobpulse.discovery.domain.CompanySeed;
import com.huy.jobpulse.discovery.domain.CompanySeedStatus;

import java.time.Instant;
import java.util.UUID;

public record CompanySeedResponse(
        UUID id,
        String companyName,
        String careersUrl,
        CompanySeedStatus status,
        Instant lastCheckedAt,
        String matchedUrl,
        String lastError
) {

    public static CompanySeedResponse from(CompanySeed seed) {
        return new CompanySeedResponse(
                seed.getId(),
                seed.getCompanyName(),
                seed.getCareersUrl(),
                seed.getStatus(),
                seed.getLastCheckedAt(),
                seed.getMatchedUrl(),
                seed.getLastError()
        );
    }
}
