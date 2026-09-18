package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;

import java.time.Instant;
import java.util.UUID;

public record JobEventEnvelope(
        int schemaVersion,
        UUID eventId,
        JobEventType eventType,
        UUID jobId,
        Instant occurredAt,
        JobSource source,
        String sourceAccount,
        String company,
        String title,
        RemotePolicy remotePolicy
) {
    public static JobEventEnvelope from(JobEvent event) {
        return new JobEventEnvelope(
                event.getSchemaVersion(),
                event.getId(),
                event.getEventType(),
                event.getJobPostingId(),
                event.getCreatedAt(),
                event.getSource(),
                event.getSourceAccount(),
                event.getCompany(),
                event.getTitle(),
                event.getRemotePolicy()
        );
    }
}
