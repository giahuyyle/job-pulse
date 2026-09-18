package com.huy.jobpulse.discovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "company_seeds")
public class CompanySeed {

    @Id
    private UUID id;

    @Column(name = "company_name", nullable = false, columnDefinition = "TEXT")
    private String companyName;

    @Column(name = "careers_url", nullable = false, unique = true,
            columnDefinition = "TEXT")
    private String careersUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "TEXT")
    private CompanySeedStatus status;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "matched_url", columnDefinition = "TEXT")
    private String matchedUrl;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    private long version;

    protected CompanySeed() {
        // Required by JPA
    }

    private CompanySeed(UUID id, String companyName, String careersUrl) {
        this.id = id;
        this.companyName = companyName;
        this.careersUrl = careersUrl;
        this.status = CompanySeedStatus.PENDING;
    }

    public static CompanySeed create(String companyName, String careersUrl) {
        String normalizedName = requireText(companyName, "companyName");
        String normalizedUrl = requireHttpsUrl(careersUrl);
        return new CompanySeed(
                UUID.randomUUID(),
                normalizedName,
                normalizedUrl
        );
    }

    public void record(
            CompanySeedStatus outcome,
            Instant checkedAt,
            String matchedUrl,
            String error
    ) {
        this.status = Objects.requireNonNull(outcome);
        this.lastCheckedAt = Objects.requireNonNull(checkedAt);
        this.matchedUrl = normalizeOptional(matchedUrl);
        this.lastError = normalizeOptional(error);
    }

    private static String requireHttpsUrl(String value) {
        String normalized = requireText(value, "careersUrl");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("careersUrl must be a valid HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("careersUrl must be a valid HTTPS URL");
        }
        return uri.normalize().toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public CompanySeedStatus getStatus() {
        return status;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getMatchedUrl() {
        return matchedUrl;
    }

    public String getLastError() {
        return lastError;
    }
}
