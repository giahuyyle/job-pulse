package com.huy.jobpulse.ingestion.domain;

import com.huy.jobpulse.jobs.domain.JobSource;
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
@Table(name = "ingestion_runs")
public class IngestionRun {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobSource source;

    @Column(name = "source_account", nullable = false, length = 160)
    private String sourceAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IngestionRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private Integer discovered;
    private Integer created;
    private Integer updated;
    private Integer unchanged;
    private Integer closed;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Version
    private long version;

    protected IngestionRun() {
        // Required by JPA
    }

    private IngestionRun(
            UUID id,
            JobSource source,
            String sourceAccount,
            Instant startedAt
    ) {
        this.id = id;
        this.source = source;
        this.sourceAccount = sourceAccount;
        this.status = IngestionRunStatus.RUNNING;
        this.startedAt = startedAt;
    }

    public static IngestionRun start(
            JobSource source,
            String sourceAccount,
            Instant startedAt
    ) {
        return new IngestionRun(
                UUID.randomUUID(),
                Objects.requireNonNull(source),
                requireText(sourceAccount, "sourceAccount"),
                Objects.requireNonNull(startedAt)
        );
    }

    public void succeed(
            int discovered,
            int created,
            int updated,
            int unchanged,
            int closed,
            Instant completedAt
    ) {
        requireRunning();
        this.status = IngestionRunStatus.SUCCEEDED;
        this.completedAt = Objects.requireNonNull(completedAt);
        this.discovered = discovered;
        this.created = created;
        this.updated = updated;
        this.unchanged = unchanged;
        this.closed = closed;
        this.failureMessage = null;
    }

    public void fail(String message, Instant completedAt) {
        requireRunning();
        this.status = IngestionRunStatus.FAILED;
        this.completedAt = Objects.requireNonNull(completedAt);
        this.failureMessage = message == null || message.isBlank()
                ? "Ingestion failed"
                : message;
    }

    private void requireRunning() {
        if (status != IngestionRunStatus.RUNNING) {
            throw new IllegalStateException(
                    "Ingestion run is already " + status
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    public UUID getId() {
        return id;
    }

    public JobSource getSource() {
        return source;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public IngestionRunStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getClosed() {
        return closed;
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}
