package com.huy.jobpulse.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ingestion_requests")
public class IngestionRequest {

    @Id
    private UUID id;

    @Column(name = "ingestion_target_id", nullable = false)
    private UUID ingestionTargetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IngestionRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "dispatch_after", nullable = false)
    private Instant dispatchAfter;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_owner")
    private UUID leaseOwner;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "retry_of_run_id")
    private UUID retryOfRunId;

    @Column(name = "trace_parent", length = 128)
    private String traceParent;

    @Column(name = "trace_state", length = 1024)
    private String traceState;

    @Column(name = "trace_baggage", columnDefinition = "TEXT")
    private String traceBaggage;

    @Version
    private long version;

    protected IngestionRequest() {
        // Required by JPA
    }

    private IngestionRequest(UUID id, UUID ingestionTargetId, Instant createdAt) {
        this.id = id;
        this.ingestionTargetId = ingestionTargetId;
        this.status = IngestionRequestStatus.PENDING;
        this.createdAt = createdAt;
        this.dispatchAfter = createdAt;
        this.correlationId = id;
    }

    public static IngestionRequest create(UUID targetId, Instant now) {
        return create(targetId, now, null, null, null);
    }

    public static IngestionRequest create(UUID targetId, Instant now,
            String traceParent, String traceState, String traceBaggage) {
        IngestionRequest request = new IngestionRequest(
                UUID.randomUUID(),
                Objects.requireNonNull(targetId),
                Objects.requireNonNull(now)
        );
        request.traceParent = traceParent;
        request.traceState = traceState;
        request.traceBaggage = traceBaggage;
        return request;
    }

    public static IngestionRequest retry(UUID targetId, UUID failedRunId,
            Instant now, String traceParent, String traceState,
            String traceBaggage) {
        IngestionRequest request = create(targetId, now,
                traceParent, traceState, traceBaggage);
        request.retryOfRunId = Objects.requireNonNull(failedRunId);
        return request;
    }

    public static IngestionRequest retry(UUID targetId, UUID failedRunId, Instant now) {
        IngestionRequest request = create(targetId, now);
        request.retryOfRunId = Objects.requireNonNull(failedRunId);
        return request;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIngestionTargetId() {
        return ingestionTargetId;
    }

    public IngestionRequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getDispatchAfter() { return dispatchAfter; }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public UUID getLeaseOwner() { return leaseOwner; }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public UUID getRetryOfRunId() { return retryOfRunId; }
    public String getTraceParent() { return traceParent; }
    public String getTraceState() { return traceState; }
    public String getTraceBaggage() { return traceBaggage; }
}
