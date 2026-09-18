package com.huy.jobpulse.jobs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "job_events")
public class JobEvent {

    @Id
    private UUID id;

    @Column(name = "job_posting_id", nullable = false)
    private UUID jobPostingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private JobEventType eventType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected JobEvent() {
        // Required by JPA
    }

    private JobEvent(
            UUID id,
            UUID jobPostingId,
            JobEventType eventType,
            Instant createdAt
    ) {
        this.id = id;
        this.jobPostingId = jobPostingId;
        this.eventType = eventType;
        this.createdAt = createdAt;
    }

    public static JobEvent created(UUID jobPostingId, Instant createdAt) {
        return new JobEvent(
                UUID.randomUUID(),
                Objects.requireNonNull(jobPostingId),
                JobEventType.CREATED,
                Objects.requireNonNull(createdAt)
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobPostingId() {
        return jobPostingId;
    }

    public JobEventType getEventType() {
        return eventType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
