package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public EventOutboxPublisher(
            JobEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            JobEventCodec codec,
            Clock clock
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.clock = clock;
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
        try {
            kafkaTemplate.send(
                    KafkaTopics.JOB_EVENTS,
                    event.getJobPostingId().toString(),
                    codec.encode(JobEventEnvelope.from(event))
            ).get(10, TimeUnit.SECONDS);
            event.markPublished(clock.instant());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not publish job event " + event.getId(),
                    exception
            );
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
