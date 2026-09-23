package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.huy.jobpulse.observability.JobPulseMetrics;
import com.huy.jobpulse.observability.TraceContextBridge;
import com.huy.jobpulse.observability.TraceContextSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    @Autowired
    public EventOutboxPublisher(
            JobEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            JobEventCodec codec,
            Clock clock,
            ObjectProvider<JobPulseMetrics> metrics,
            ObjectProvider<TraceContextBridge> traceContexts
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.clock = clock;
        this.metrics = metrics.getIfAvailable();
        this.traceContexts = traceContexts.getIfAvailable();
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
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.events.publish-delay-ms:1000}",
            initialDelayString = "${jobpulse.events.publish-initial-delay-ms:1000}"
    )
    @Transactional
    public int publishBatch() {
        List<JobEvent> events = repository.claimUnpublished(BATCH_SIZE);
        for (JobEvent event : events) {
            publish(event);
        }
        logBacklogAge();
        return events.size();
    }

    private void publish(JobEvent event) {
        if (traceContexts == null) {
            publishWithinTrace(event, null);
            return;
        }
        TraceContextSnapshot snapshot = new TraceContextSnapshot(
                event.getTraceParent(),
                event.getTraceState(),
                event.getTraceBaggage()
        );
        try (var trace = traceContexts.continueTrace(
                snapshot, "jobpulse.outbox.publish")) {
            publishWithinTrace(event, trace);
        }
    }

    private void publishWithinTrace(JobEvent event,
            TraceContextBridge.ContinuedTrace trace) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.JOB_EVENTS,
                    event.getJobPostingId().toString(),
                    codec.encode(JobEventEnvelope.from(event))
            ).get(10, TimeUnit.SECONDS);
            event.markPublished(clock.instant());
            if (metrics != null) metrics.recordOutboxPublication("success");
            LOGGER.atInfo()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("jobId", event.getJobPostingId())
                    .addKeyValue("eventType", event.getEventType().name().toLowerCase())
                    .log("Outbox event published to Kafka");
        } catch (InterruptedException exception) {
            if (trace != null) trace.error(exception);
            Thread.currentThread().interrupt();
            event.markPublishFailed(clock.instant(), "Kafka publication interrupted");
            if (metrics != null) metrics.recordOutboxPublication("failed");
            LOGGER.atError()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("jobId", event.getJobPostingId())
                    .addKeyValue("eventType", event.getEventType().name().toLowerCase())
                    .setCause(exception)
                    .log("Kafka publication interrupted");
        } catch (Exception exception) {
            if (trace != null) trace.error(exception);
            String detail = exception.getCause() == null
                    ? exception.getMessage() : exception.getCause().getMessage();
            event.markPublishFailed(clock.instant(), detail);
            if (metrics != null) metrics.recordOutboxPublication("failed");
            LOGGER.atError()
                    .addKeyValue("eventId", event.getId())
                    .addKeyValue("jobId", event.getJobPostingId())
                    .addKeyValue("eventType", event.getEventType().name().toLowerCase())
                    .setCause(exception)
                    .log("Could not publish outbox event to Kafka");
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
