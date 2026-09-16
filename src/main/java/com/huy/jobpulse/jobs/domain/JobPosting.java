package com.huy.jobpulse.jobs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "job_postings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_source_identity",
                columnNames = {
                        "source",
                        "source_account",
                        "source_job_id"
                }
        )
)
public class JobPosting {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobSource source;

    @Column(name = "source_account", nullable = false, length = 160)
    private String sourceAccount;

    @Column(name = "source_job_id", nullable = false, length = 255)
    private String sourceJobId;

    @Column(nullable = false, length = 255)
    private String company;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "employment_type", length = 80)
    private String employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_policy", nullable = false, length = 32)
    private RemotePolicy remotePolicy;

    @Column(name = "apply_url", nullable = false, columnDefinition = "TEXT")
    private String applyUrl;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Version
    private long version;

    protected JobPosting() {
        // Required by JPA
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public JobSource getSource() {
        return source;
    }

    public String getCompany() {
        return company;
    }

    public String getSourceJobId() {
        return sourceJobId;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    // Custom methods
    public void markSeen(Instant observedAt) {
        this.lastSeenAt = observedAt;
    }

    public void close(Instant observedAt) {
        this.status = JobStatus.CLOSED;
        this.lastSeenAt = observedAt;
    }

    public void reopen(Instant observedAt) {
        this.status = JobStatus.ACTIVE;
        this.lastSeenAt = observedAt;
    }
}
