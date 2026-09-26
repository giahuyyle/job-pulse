package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.huy.jobpulse.observability.JobPulseMetrics;
import com.huy.jobpulse.observability.TraceContextBridge;
import com.huy.jobpulse.observability.TraceContextSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class EventOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            EventOutboxPublisher.class
    );
    private static final int BATCH_SIZE = 50;

    private final JobEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JobEventCodec codec;
    private final Clock clock;
    private final JobPulseMetrics metrics;
    private final TraceContextBridge traceContexts;
    private final OutboxLeaseService leases;

    @Autowired
    public EventOutboxPublisher(
            JobEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            JobEventCodec codec,
            Clock clock,
            ObjectProvider<JobPulseMetrics> metrics,
            ObjectProvider<TraceContextBridge> traceContexts,
            OutboxLeaseService leases
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.clock = clock;
        this.metrics = metrics.getIfAvailable();
        this.traceContexts = traceContexts.getIfAvailable();
        this.leases = leases;
    }

    public EventOutboxPublisher(JobEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            JobEventCodec codec,
            Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.clock = clock;
        this.metrics = null;
        this.traceContexts = null;
        this.leases = null;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.events.publish-delay-ms:1000}",
            initialDelayString = "${jobpulse.events.publish-initial-delay-ms:1000}"
    )
    public int publishBatch() {
        int attempted = 0;
        if (leases == null) {
            for (JobEvent event : repository.claimUnpublished(BATCH_SIZE)) {
                attempted++;
                if (!publish(event, null)) break;
            }
        } else {
            for (int index = 0; index < BATCH_SIZE; index++) {
                var claimed = leases.claim(1);
                if (claimed.isEmpty()) break;
                attempted++;
                var next = claimed.getFirst();
                if (!publish(next.event(), next.owner())) break;
            }
        }
        logBacklogAge();
        return attempted;
    }

    private boolean publish(JobEvent event, java.util.UUID owner) {
        if (traceContexts == null) {
            return publishWithinTrace(event, owner, null);
        }
        TraceContextSnapshot snapshot = new TraceContextSnapshot(
                event.getTraceParent(),
                event.getTraceState(),
                event.getTraceBaggage()
        );
        try (var trace = traceContexts.continueTrace(
                snapshot, "jobpulse.outbox.publish")) {
            return publishWithinTrace(event, owner, trace);
        }
    }

    private boolean publishWithinTrace(JobEvent event, java.util.UUID owner,
            TraceContextBridge.ContinuedTrace trace) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.JOB_EVENTS,
                    event.getJobPostingId().toString(),
                    codec.encode(JobEventEnvelope.from(event))
            ).get(10, TimeUnit.SECONDS);
            if (leases == null) event.markPublished(clock.instant());
            else if (!leases.published(event.getId(), owner)) {
                LOGGER.atWarn().addKeyValue("eventId", event.getId())
                        .log("Outbox acknowledgement arrived after lease ownership changed");
                return false;
            }
            if (metrics != null) metrics.recordOutboxPublication("success");
            LOGGER.atInfo()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("jobId", event.getJobPostingId())
                    .addKeyValue("eventType", event.getEventType().name().toLowerCase())
                    .log("Outbox event published to Kafka");
            return true;
        } catch (InterruptedException exception) {
            if (trace != null) trace.error(exception);
            Thread.currentThread().interrupt();
            if (leases == null) event.markPublishFailed(clock.instant(), "Kafka publication interrupted");
            else leases.failed(event.getId(), owner, "Kafka publication interrupted");
            if (metrics != null) metrics.recordOutboxPublication("failed");
            if (metrics != null) metrics.recordOutboxRetry(event.getEventType().name(),
                    event.getPublishAttempts() + (leases == null ? 0 : 1) >= 10
                            ? "exhausted" : "scheduled");
            LOGGER.atError()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("jobId", event.getJobPostingId())
                    .addKeyValue("eventType", event.getEventType().name().toLowerCase())
                    .setCause(exception)
                    .log("Kafka publication interrupted");
            return false;
        } catch (Exception exception) {
            if (trace != null) trace.error(exception);
            String detail = exception.getCause() == null
                    ? exception.getMessage() : exception.getCause().getMessage();
            if (leases == null) event.markPublishFailed(clock.instant(), detail);
            else leases.failed(event.getId(), owner, detail);
            if (metrics != null) metrics.recordOutboxPublication("failed");
            if (metrics != null) metrics.recordOutboxRetry(event.getEventType().name(),
                    event.getPublishAttempts() + (leases == null ? 0 : 1) >= 10
                            ? "exhausted" : "scheduled");
            LOGGER.atError()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("jobId", event.getJobPostingId())
                    .addKeyValue("eventType", event.getEventType().name().toLowerCase())
                    .setCause(exception)
                    .log("Could not publish outbox event to Kafka");
            return false;
        }
    }

    private void logBacklogAge() {
        Instant oldest = repository.findOldestUnpublishedAt();
        if (oldest == null) {
            return;
        }
        Duration age = Duration.between(oldest, clock.instant());
        if (age.compareTo(Duration.ofMinutes(5)) >= 0) {
            LOGGER.warn(
                    "Kafka outbox backlog: {} unpublished events; oldest age {}",
                    repository.countByPublishedAtIsNull(),
                    age
            );
        }
    }
}
