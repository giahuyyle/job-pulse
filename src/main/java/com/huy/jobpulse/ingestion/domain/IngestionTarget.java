package com.huy.jobpulse.ingestion.domain;

import com.huy.jobpulse.jobs.domain.JobSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ingestion_targets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ingestion_target",
                columnNames = {"source", "source_account"}
        )
)
public class IngestionTarget {

    private static final int MIN_INTERVAL_MINUTES = 15;
    private static final int MAX_INTERVAL_MINUTES = 1440;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobSource source;

    @Column(name = "source_account", nullable = false, length = 160)
    private String sourceAccount;

    @Column(nullable = false, length = 255)
    private String company;

    @Column(name = "careers_url", columnDefinition = "TEXT")
    private String careersUrl;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    private long version;

    protected IngestionTarget() {
        // Required by JPA
    }

    private IngestionTarget(
            UUID id,
            JobSource source,
            String sourceAccount,
            String company,
            String careersUrl,
            int intervalMinutes,
            Instant nextRunAt
    ) {
        this.id = id;
        this.source = source;
        this.sourceAccount = sourceAccount;
        this.company = company;
        this.careersUrl = careersUrl;
        this.enabled = true;
        this.intervalMinutes = intervalMinutes;
        this.nextRunAt = nextRunAt;
    }

    public static IngestionTarget create(
            JobSource source,
            String sourceAccount,
            String company,
            String careersUrl,
            int intervalMinutes,
            Instant now
    ) {
        validateInterval(intervalMinutes);
        UUID id = UUID.randomUUID();
        return new IngestionTarget(
                id,
                Objects.requireNonNull(source),
                requireText(sourceAccount, "sourceAccount", 160),
                requireText(company, "company", 255),
                normalizeOptional(careersUrl),
                intervalMinutes,
                nextRunWithOffset(Objects.requireNonNull(now), id, 1)
        );
    }

    public void markSucceeded(Instant now) {
        Instant completedAt = Objects.requireNonNull(now);
        lastSuccessAt = completedAt;
        lastError = null;
        nextRunAt = nextRunWithOffset(
                completedAt,
                id,
                intervalMinutes
        );
    }

    public void markScheduled(Instant now) {
        nextRunAt = nextRunWithOffset(
                Objects.requireNonNull(now),
                id,
                intervalMinutes
        );
    }

    public void markFailed(Instant now, String message) {
        Instant failedAt = Objects.requireNonNull(now);
        lastError = message == null || message.isBlank()
                ? "Ingestion failed"
                : message;
        nextRunAt = nextRunWithOffset(
                failedAt,
                id,
                Math.min(intervalMinutes, 15)
        );
    }

    public void setEnabled(boolean enabled, Instant now) {
        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
        if (enabled && !wasEnabled) {
            this.nextRunAt = nextRunWithOffset(
                    Objects.requireNonNull(now),
                    id,
                    1
            );
        }
    }

    private static Instant nextRunWithOffset(
            Instant now,
            UUID id,
            int minutes
    ) {
        long offsetSeconds = Math.floorMod(id.getLeastSignificantBits(), 60);
        return now.plus(minutes, ChronoUnit.MINUTES)
                .plus(offsetSeconds, ChronoUnit.SECONDS);
    }

    private static void validateInterval(int intervalMinutes) {
        if (intervalMinutes < MIN_INTERVAL_MINUTES
                || intervalMinutes > MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException(
                    "intervalMinutes must be between 15 and 1440"
            );
        }
    }

    private static String requireText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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

    public String getCompany() {
        return company;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public String getLastError() {
        return lastError;
    }
}
