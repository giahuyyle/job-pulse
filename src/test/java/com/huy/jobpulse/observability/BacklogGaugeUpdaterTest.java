package com.huy.jobpulse.observability;

import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacklogGaugeUpdaterTest {

    @Test
    void refreshesCachedGaugesFromRepositories() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobEventRepository events = mock(JobEventRepository.class);
        IngestionRequestRepository requests = mock(IngestionRequestRepository.class);
        IngestionDeadLetterRepository deadLetters = mock(IngestionDeadLetterRepository.class);
        Instant now = Instant.parse("2026-09-23T08:00:00Z");
        when(events.countByPublishedAtIsNull()).thenReturn(7L);
        when(events.findOldestUnpublishedAt()).thenReturn(now.minusSeconds(90));
        when(requests.countByStatus(IngestionRequestStatus.PENDING)).thenReturn(4L);
        when(requests.countByStatus(IngestionRequestStatus.RUNNING)).thenReturn(2L);
        when(deadLetters.countByReplayedAtIsNull()).thenReturn(3L);

        BacklogGaugeUpdater updater = new BacklogGaugeUpdater(registry, events,
                requests, deadLetters, Clock.fixed(now, ZoneOffset.UTC));
        updater.refresh();

        assertGauge(registry, "jobpulse.outbox.unpublished", 7);
        assertGauge(registry, "jobpulse.outbox.oldest_age_seconds", 90);
        assertGauge(registry, "jobpulse.ingestion.queued", 4);
        assertGauge(registry, "jobpulse.ingestion.running", 2);
        assertGauge(registry, "jobpulse.messages.dead_letter_count", 3);
    }

    private static void assertGauge(SimpleMeterRegistry registry, String name,
            double expected) {
        assertThat(registry.get(name).gauge().value()).isEqualTo(expected);
    }
}
