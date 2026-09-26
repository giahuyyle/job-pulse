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

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_publish_error", columnDefinition = "TEXT")
    private String lastPublishError;

    @Column(name = "last_publish_attempt_at")
    private Instant lastPublishAttemptAt;

    @Column(name = "retry_requested_at")
    private Instant retryRequestedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "publish_status", nullable = false, length = 24)
    private String publishStatus;

    @Column(name = "lease_owner")
    private UUID leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "trace_parent", length = 128)
    private String traceParent;

    @Column(name = "trace_state", length = 1024)
    private String traceState;

    @Column(name = "trace_baggage", columnDefinition = "TEXT")
    private String traceBaggage;

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
        this.nextAttemptAt = createdAt;
        this.publishStatus = "PENDING";
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
        publishAttempts++;
        lastPublishAttemptAt = Objects.requireNonNull(publishedAt);
        lastPublishError = null;
        retryRequestedAt = null;
        nextAttemptAt = publishedAt;
        publishStatus = "PUBLISHED";
        leaseOwner = null;
        leaseExpiresAt = null;
        if (this.publishedAt == null) {
            this.publishedAt = publishedAt;
        }
    }

    public void markPublishFailed(Instant attemptedAt, String error) {
        publishAttempts++;
        lastPublishAttemptAt = Objects.requireNonNull(attemptedAt);
        lastPublishError = error == null || error.isBlank()
                ? "Kafka publication failed" : error;
        long base = Math.min(900_000L, 5_000L << Math.min(17, publishAttempts - 1));
        long jitter = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(Math.max(1, base / 4));
        nextAttemptAt = attemptedAt.plusMillis(Math.min(900_000L, base + jitter));
        publishStatus = publishAttempts >= 10 ? "FAILED" : "PENDING";
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    public int getPublishAttempts() { return publishAttempts; }
    public String getLastPublishError() { return lastPublishError; }
    public Instant getLastPublishAttemptAt() { return lastPublishAttemptAt; }
    public Instant getRetryRequestedAt() { return retryRequestedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public boolean isPublishFailed() { return "FAILED".equals(publishStatus); }
    public String getPublishStatus() { return publishStatus; }
    public UUID getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }

    public void claim(UUID owner, Instant expiresAt) {
        if (!"PENDING".equals(publishStatus)) throw new IllegalStateException("Event is not pending");
        publishStatus = "PUBLISHING";
        leaseOwner = Objects.requireNonNull(owner);
        leaseExpiresAt = Objects.requireNonNull(expiresAt);
    }
    public String getTraceParent() { return traceParent; }
    public String getTraceState() { return traceState; }
    public String getTraceBaggage() { return traceBaggage; }

    public void recordTraceContext(String traceParent, String traceState,
            String traceBaggage) {
        this.traceParent = traceParent;
        this.traceState = traceState;
        this.traceBaggage = traceBaggage;
    }

    public void requestPublishRetry(Instant requestedAt) {
        if (publishedAt != null) {
            throw new IllegalArgumentException("Published events cannot be retried");
        }
        if (!"FAILED".equals(publishStatus) && !"PENDING".equals(publishStatus)) {
            throw new IllegalArgumentException("Publishing events cannot be retried manually");
        }
        retryRequestedAt = Objects.requireNonNull(requestedAt);
        lastPublishError = null;
        nextAttemptAt = requestedAt;
        publishAttempts = 0;
        publishStatus = "PENDING";
        leaseOwner = null;
        leaseExpiresAt = null;
    }
}
