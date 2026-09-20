package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.events.JobEventEnvelope;
import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import java.time.Instant;
import java.util.UUID;

public record AdminEventResponse(UUID id, UUID jobId, JobEventType type,
        int schemaVersion, String status, Instant createdAt, Instant publishedAt, int publishAttempts,
        Instant lastPublishAttemptAt, String lastPublishError,
        JobEventEnvelope payload) {
    public static AdminEventResponse from(JobEvent event) {
        String status = event.getPublishedAt() != null ? "PUBLISHED"
                : event.getLastPublishError() != null ? "FAILED" : "UNPUBLISHED";
        return new AdminEventResponse(event.getId(), event.getJobPostingId(),
                event.getEventType(), event.getSchemaVersion(), status,
                event.getCreatedAt(), event.getPublishedAt(),
                event.getPublishAttempts(), event.getLastPublishAttemptAt(),
                event.getLastPublishError(), JobEventEnvelope.from(event));
    }
}
