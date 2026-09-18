package com.huy.jobpulse.alerts.api;

import com.huy.jobpulse.alerts.domain.SavedSearch;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;

import java.time.Instant;
import java.util.UUID;

public record SavedSearchResponse(
        UUID id,
        String name,
        String query,
        String company,
        JobSource source,
        RemotePolicy remotePolicy,
        String location,
        boolean enabled,
        Instant createdAt
) {
    public static SavedSearchResponse from(SavedSearch search) {
        return new SavedSearchResponse(
                search.getId(),
                search.getName(),
                search.getQuery(),
                search.getCompany(),
                search.getSource(),
                search.getRemotePolicy(),
                search.getLocation(),
                search.isEnabled(),
                search.getCreatedAt()
        );
    }
}
