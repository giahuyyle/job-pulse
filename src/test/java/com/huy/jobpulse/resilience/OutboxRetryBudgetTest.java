package com.huy.jobpulse.resilience;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRetryBudgetTest {
    @Test
    void failedEventRequiresDeliberateRetryAfterTenAttempts() {
        Instant now = Instant.parse("2026-09-26T00:00:00Z");
        var posting = JobPosting.create(JobSource.ASHBY, "test", "job-1",
                "Test", "Engineer", "Remote", "Description", "Full-time",
                RemotePolicy.REMOTE, "https://example.com/apply", now, now);
        JobEvent event = JobEvent.capture(posting, JobEventType.CREATED, now);
        event.claim(java.util.UUID.randomUUID(), now.plusSeconds(30));
        assertThatThrownBy(() -> event.requestPublishRetry(now))
                .hasMessageContaining("Publishing events");
        for (int attempt = 1; attempt <= 10; attempt++) {
            event.markPublishFailed(now.plusSeconds(attempt), "Kafka unavailable");
            assertThat(event.getNextAttemptAt()).isAfter(now.plusSeconds(attempt));
        }
        assertThat(event.isPublishFailed()).isTrue();
        event.requestPublishRetry(now.plusSeconds(100));
        assertThat(event.getPublishStatus()).isEqualTo("PENDING");
        assertThat(event.getPublishAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(100));
    }
}
