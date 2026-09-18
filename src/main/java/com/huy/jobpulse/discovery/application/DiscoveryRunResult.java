package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.discovery.domain.CompanySeedStatus;

import java.util.List;

public record DiscoveryRunResult(
        int checked,
        int added,
        int alreadyExists,
        int needsReview,
        int errors,
        List<DiscoverySeedOutcome> outcomes
) {

    public static DiscoveryRunResult from(List<DiscoverySeedOutcome> outcomes) {
        return new DiscoveryRunResult(
                outcomes.size(),
                count(outcomes, CompanySeedStatus.ADDED),
                count(outcomes, CompanySeedStatus.ALREADY_EXISTS),
                count(outcomes, CompanySeedStatus.NEEDS_REVIEW),
                count(outcomes, CompanySeedStatus.ERROR),
                List.copyOf(outcomes)
        );
    }

    private static int count(
            List<DiscoverySeedOutcome> outcomes,
            CompanySeedStatus status
    ) {
        return (int) outcomes.stream()
                .filter(outcome -> outcome.status() == status)
                .count();
    }
}
