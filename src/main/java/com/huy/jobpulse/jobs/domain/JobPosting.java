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
import java.util.Objects;
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

    private JobPosting(
            UUID id,
            JobSource source,
            String sourceAccount,
            String sourceJobId,
            String company,
            String title,
            String location,
            String description,
            String employmentType,
            RemotePolicy remotePolicy,
            String applyUrl,
            Instant postedAt,
            Instant firstSeenAt,
            Instant lastSeenAt,
            JobStatus status,
            String contentHash
    ) {
        this.id = id;
        this.source = source;
        this.sourceAccount = sourceAccount;
        this.sourceJobId = sourceJobId;
        this.company = company;
        this.title = title;
        this.location = location;
        this.description = description;
        this.employmentType = employmentType;
        this.remotePolicy = remotePolicy;
        this.applyUrl = applyUrl;
        this.postedAt = postedAt;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.status = status;
        this.contentHash = contentHash;
    }

    public static JobPosting create(
            JobSource source,
            String sourceAccount,
            String sourceJobId,
            String company,
            String title,
            String location,
            String description,
            String employmentType,
            RemotePolicy remotePolicy,
            String applyUrl,
            Instant postedAt,
            Instant observedAt
    ) {
        requireText(sourceAccount, "sourceAccount");
        requireText(sourceJobId, "sourceJobId");
        requireText(company, "company");
        requireText(title, "title");
        requireText(applyUrl, "applyUrl");

        if (source == null) {
            throw new IllegalArgumentException(
                    "source must not be null"
            );
        }

        if (remotePolicy == null) {
            remotePolicy = RemotePolicy.UNSPECIFIED;
        }

        String contentHash = JobFingerprint.create(
                title,
                location,
                description,
                applyUrl
        );

        return new JobPosting(
                UUID.randomUUID(),
                source,
                sourceAccount.strip(),
                sourceJobId.strip(),
                company.strip(),
                title.strip(),
                location,
                description,
                employmentType,
                remotePolicy,
                applyUrl.strip(),
                postedAt,
                observedAt,
                observedAt,
                JobStatus.ACTIVE,
                contentHash
        );
    }

    private static void requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
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

    public String getDescription() {
        return description;
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

    public boolean refresh(
            String company,
            String title,
            String location,
            String description,
            String employmentType,
            RemotePolicy remotePolicy,
            String applyUrl,
            Instant postedAt,
            Instant observedAt
    ) {
        requireText(company, "company");
        requireText(title, "title");
        requireText(applyUrl, "applyUrl");

        String normalizedCompany = company.strip();
        String normalizedTitle = title.strip();
        String normalizedApplyUrl = applyUrl.strip();
        RemotePolicy normalizedRemotePolicy = remotePolicy == null
                ? RemotePolicy.UNSPECIFIED
                : remotePolicy;
        String newContentHash = JobFingerprint.create(
                title,
                location,
                description,
                applyUrl
        );

        boolean changed = !Objects.equals(this.company, normalizedCompany)
                || !Objects.equals(this.title, normalizedTitle)
                || !Objects.equals(this.location, location)
                || !Objects.equals(this.description, description)
                || !Objects.equals(this.employmentType, employmentType)
                || this.remotePolicy != normalizedRemotePolicy
                || !Objects.equals(this.applyUrl, normalizedApplyUrl)
                || !Objects.equals(this.postedAt, postedAt)
                || !Objects.equals(this.contentHash, newContentHash)
                || this.status != JobStatus.ACTIVE;

        if (changed) {
            this.company = normalizedCompany;
            this.title = normalizedTitle;
            this.location = location;
            this.description = description;
            this.employmentType = employmentType;
            this.remotePolicy = normalizedRemotePolicy;
            this.applyUrl = normalizedApplyUrl;
            this.postedAt = postedAt;
            this.contentHash = newContentHash;
            this.status = JobStatus.ACTIVE;
        }

        this.lastSeenAt = observedAt;
        return changed;
    }
}
