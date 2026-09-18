package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    @Test
    void marksPublishedOnlyAfterKafkaConfirmsTheSend() {
        JobEvent event = event();
        JobEventRepository repository = repositoryReturning(event);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        EventOutboxPublisher publisher = new EventOutboxPublisher(
                repository,
                kafka,
                codec(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(publisher.publishBatch()).isOne();
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void leavesEventUnpublishedWhenKafkaRejectsTheSend() {
        JobEvent event = event();
        JobEventRepository repository = repositoryReturning(event);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("Kafka unavailable")
                ));

        EventOutboxPublisher publisher = new EventOutboxPublisher(
                repository,
                kafka,
                codec(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(publisher::publishBatch)
                .hasMessageContaining("Could not publish job event");
        assertThat(event.getPublishedAt()).isNull();
    }

    private static JobEventRepository repositoryReturning(JobEvent event) {
        JobEventRepository repository = mock(JobEventRepository.class);
        when(repository.claimUnpublished(50)).thenReturn(List.of(event));
        return repository;
    }

    private static JobEvent event() {
        JobPosting posting = JobPosting.create(
                JobSource.ASHBY,
                "example",
                "job-1",
                "Example",
                "Backend Engineer",
                "Remote",
                "Build services",
                "Full-time",
                RemotePolicy.REMOTE,
                "https://example.com/job-1",
                NOW,
                NOW
        );
        return JobEvent.capture(posting, JobEventType.CREATED, NOW);
    }

    private static JobEventCodec codec() {
        return new JobEventCodec(new tools.jackson.databind.ObjectMapper());
    }
}
