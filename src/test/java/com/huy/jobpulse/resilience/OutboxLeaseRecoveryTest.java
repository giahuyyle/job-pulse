package com.huy.jobpulse.resilience;

import com.huy.jobpulse.events.OutboxLeaseService;
import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OutboxLeaseRecoveryTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired OutboxLeaseService leases;
    @Autowired JobEventRepository events;
    @Autowired JobPostingRepository postings;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        events.deleteAll();
        postings.deleteAll();
    }

    @Test
    void expiredLeaseCanBeReclaimedAndStaleOwnerCannotComplete() {
        UUID id = createEvent();
        var first = leases.claim(1).getFirst();
        assertThat(first.event().getId()).isEqualTo(id);
        assertThat(events.findById(id).orElseThrow().getPublishStatus()).isEqualTo("PUBLISHING");
        assertThat(leases.claim(1)).isEmpty();

        jdbc.update("UPDATE job_events SET lease_expires_at = now() - interval '1 second' WHERE id = ?", id);
        var second = leases.claim(1).getFirst();
        assertThat(second.event().getId()).isEqualTo(id);
        assertThat(second.owner()).isNotEqualTo(first.owner());
        assertThat(leases.published(id, first.owner())).isFalse();
        assertThat(leases.published(id, second.owner())).isTrue();
        assertThat(events.findById(id).orElseThrow().getPublishStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void failedPublicationWaitsForItsNextAttempt() {
        UUID id = createEvent();
        var claim = leases.claim(1).getFirst();
        assertThat(leases.failed(id, claim.owner(), "Kafka unavailable")).isTrue();
        JobEvent event = events.findById(id).orElseThrow();
        assertThat(event.getPublishStatus()).isEqualTo("PENDING");
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(leases.claim(1)).isEmpty();

        jdbc.update("UPDATE job_events SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
        assertThat(leases.claim(1).getFirst().event().getId()).isEqualTo(id);
    }

    private UUID createEvent() {
        Instant now = Instant.now();
        JobPosting posting = postings.save(JobPosting.create(JobSource.LEVER,
                "outbox-" + UUID.randomUUID(), UUID.randomUUID().toString(),
                "Recovery test", "Engineer", "Remote", "Description", "Full-time",
                RemotePolicy.REMOTE, "https://example.com/apply", now, now));
        return events.save(JobEvent.capture(posting, JobEventType.CREATED, now)).getId();
    }
}
