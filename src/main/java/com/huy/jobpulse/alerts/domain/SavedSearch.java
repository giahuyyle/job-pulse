package com.huy.jobpulse.alerts.domain;

import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
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
@Table(name = "saved_searches")
public class SavedSearch {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 150)
    private String query;

    @Column(length = 255)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private JobSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_policy", length = 32)
    private RemotePolicy remotePolicy;

    @Column(length = 500)
    private String location;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected SavedSearch() {
        // Required by JPA
    }

    private SavedSearch(
            UUID id,
            String name,
            String query,
            String company,
            JobSource source,
            RemotePolicy remotePolicy,
            String location,
            Instant createdAt
    ) {
        this.id = id;
        this.name = requireName(name);
        apply(query, company, source, remotePolicy, location);
        this.enabled = true;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static SavedSearch create(
            String name,
            String query,
            String company,
            JobSource source,
            RemotePolicy remotePolicy,
            String location,
            Instant createdAt
    ) {
        return new SavedSearch(
                UUID.randomUUID(),
                name,
                query,
                company,
                source,
                remotePolicy,
                location,
                createdAt
        );
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void apply(
            String query,
            String company,
            JobSource source,
            RemotePolicy remotePolicy,
            String location
    ) {
        this.query = query;
        this.company = company;
        this.source = source;
        this.remotePolicy = remotePolicy;
        this.location = location;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String normalized = name.strip();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "name must not exceed 100 characters"
            );
        }
        return normalized;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getQuery() { return query; }
    public String getCompany() { return company; }
    public JobSource getSource() { return source; }
    public RemotePolicy getRemotePolicy() { return remotePolicy; }
    public String getLocation() { return location; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
