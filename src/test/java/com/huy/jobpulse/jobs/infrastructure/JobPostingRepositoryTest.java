package com.huy.jobpulse.jobs.infrastructure;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class JobPostingRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    JobPostingRepository repository;

    @Test
    void rejectsDuplicateSourceIdentity() {
        repository.saveAndFlush(posting("job-123"));

        assertThatThrownBy(() -> repository.saveAndFlush(posting("job-123")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JobPosting posting(String sourceJobId) {
        return JobPosting.create(
                JobSource.GREENHOUSE,
                "test-board",
                sourceJobId,
                "Example Co",
                "Backend Engineer",
                "Remote",
                "Build APIs",
                "Full-time",
                RemotePolicy.REMOTE,
                "https://example.com/jobs/" + sourceJobId,
                null,
                Instant.parse("2026-09-01T12:00:00Z"));
    }
}
