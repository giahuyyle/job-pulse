package com.huy.jobpulse.observability;

import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BacklogGaugeUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(BacklogGaugeUpdater.class);

    private final JobEventRepository events;
    private final IngestionRequestRepository requests;
    private final IngestionDeadLetterRepository deadLetters;
    private final Clock clock;
    private final AtomicLong unpublished = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong running = new AtomicLong();
    private final AtomicLong deadLetterCount = new AtomicLong();

    public BacklogGaugeUpdater(MeterRegistry registry,
            JobEventRepository events,
            IngestionRequestRepository requests,
            IngestionDeadLetterRepository deadLetters,
            Clock clock) {
        this.events = events;
        this.requests = requests;
        this.deadLetters = deadLetters;
        this.clock = clock;
        register(registry, "jobpulse.outbox.unpublished", unpublished,
                "Unpublished transactional outbox events");
        register(registry, "jobpulse.outbox.oldest_age_seconds", oldestAgeSeconds,
                "Age of the oldest unpublished outbox event in seconds");
        register(registry, "jobpulse.ingestion.queued", queued,
                "Queued ingestion requests");
        register(registry, "jobpulse.ingestion.running", running,
                "Running ingestion requests");
        register(registry, "jobpulse.messages.dead_letter_count", deadLetterCount,
                "Unreplayed ingestion dead-letter messages");
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.observability.backlog-refresh-ms:15000}",
            initialDelayString = "${jobpulse.observability.backlog-initial-delay-ms:0}"
    )
    public void refresh() {
        try {
            unpublished.set(events.countByPublishedAtIsNull());
            var oldest = events.findOldestUnpublishedAt();
            oldestAgeSeconds.set(oldest == null ? 0 : Math.max(0,
                    Duration.between(oldest, clock.instant()).toSeconds()));
            queued.set(requests.countByStatus(IngestionRequestStatus.PENDING));
            running.set(requests.countByStatus(IngestionRequestStatus.RUNNING));
            deadLetterCount.set(deadLetters.countByReplayedAtIsNull());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not refresh operational backlog gauges", exception);
        }
    }

    private static void register(MeterRegistry registry, String name,
            AtomicLong value, String description) {
        Gauge.builder(name, value, AtomicLong::get)
                .description(description)
                .register(registry);
    }
}
