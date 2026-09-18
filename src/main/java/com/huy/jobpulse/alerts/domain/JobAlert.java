package com.huy.jobpulse.alerts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "job_alerts")
public class JobAlert {

    @Id
    private UUID id;

    @Column(name = "saved_search_id", nullable = false)
    private UUID savedSearchId;

    @Column(name = "job_posting_id", nullable = false)
    private UUID jobPostingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    private long version;

    protected JobAlert() {
        // Required by JPA
    }

    private JobAlert(
            UUID id,
            UUID savedSearchId,
            UUID jobPostingId,
            Instant createdAt
    ) {
        this.id = id;
        this.savedSearchId = savedSearchId;
        this.jobPostingId = jobPostingId;
        this.createdAt = createdAt;
    }

    public static JobAlert create(
            UUID savedSearchId,
            UUID jobPostingId,
            Instant createdAt
    ) {
        return new JobAlert(
                UUID.randomUUID(),
                Objects.requireNonNull(savedSearchId),
                Objects.requireNonNull(jobPostingId),
                Objects.requireNonNull(createdAt)
        );
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(now);
        }
    }

    public UUID getId() { return id; }
    public UUID getSavedSearchId() { return savedSearchId; }
    public UUID getJobPostingId() { return jobPostingId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
