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

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobSource source;

    @Column(name = "source_account", nullable = false, length = 160)
    private String sourceAccount;

    @Column(nullable = false, length = 255)
    private String company;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_policy", nullable = false, length = 32)
    private RemotePolicy remotePolicy;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected JobEvent() {
        // Required by JPA
    }

    private JobEvent(
            UUID id,
            JobPosting posting,
            JobEventType eventType,
            Instant createdAt
    ) {
        this.id = id;
        this.jobPostingId = posting.getId();
        this.eventType = eventType;
        this.createdAt = createdAt;
        this.schemaVersion = 1;
        this.source = posting.getSource();
        this.sourceAccount = posting.getSourceAccount();
        this.company = posting.getCompany();
        this.title = posting.getTitle();
        this.remotePolicy = posting.getRemotePolicy();
    }

    public static JobEvent capture(
            JobPosting posting,
            JobEventType eventType,
            Instant createdAt
    ) {
        return new JobEvent(
                UUID.randomUUID(),
                Objects.requireNonNull(posting),
                Objects.requireNonNull(eventType),
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

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public JobSource getSource() {
        return source;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public RemotePolicy getRemotePolicy() {
        return remotePolicy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt == null) {
            this.publishedAt = Objects.requireNonNull(publishedAt);
        }
    }
}
