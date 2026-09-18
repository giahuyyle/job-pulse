package com.huy.jobpulse.alerts.api;

import com.huy.jobpulse.alerts.application.AlertView;
import com.huy.jobpulse.jobs.api.JobSummaryResponse;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID savedSearchId,
        Instant createdAt,
        Instant readAt,
        JobSummaryResponse job
) {
    public static AlertResponse from(AlertView view) {
        return new AlertResponse(
                view.alert().getId(),
                view.alert().getSavedSearchId(),
                view.alert().getCreatedAt(),
                view.alert().getReadAt(),
                JobSummaryResponse.from(view.job())
        );
    }
}
