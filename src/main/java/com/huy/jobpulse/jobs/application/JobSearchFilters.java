package com.huy.jobpulse.jobs.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;

public record JobSearchFilters(
        String query,
        String company,
        JobSource source,
        RemotePolicy remotePolicy,
        String location
) {
    public JobSearchFilters {
        query = normalize(query, "query", 150);
        company = normalize(company, "company", 255);
        location = normalize(location, "location", 500);
    }

    private static String normalize(
            String value,
            String field,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }
}
