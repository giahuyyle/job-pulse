package com.huy.jobpulse.observability;

import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class PipelineMetrics {
    private final MeterRegistry registry;
    private final Timer alertLatency;

    public PipelineMetrics(MeterRegistry registry,
            IngestionRequestRepository requests,
            JobEventRepository events,
            Clock clock) {
        this.registry = registry;
        this.alertLatency = Timer.builder("jobpulse.alert.delivery")
                .description("Time from job CREATED event to in-app alert")
                .register(registry);
        Gauge.builder("jobpulse.outbox.unpublished.count", events,
                        JobEventRepository::countByPublishedAtIsNull)
                .description("Unpublished Kafka outbox rows")
                .register(registry);
        Gauge.builder("jobpulse.outbox.oldest.age.seconds", events, repo -> {
                    var oldest = repo.findOldestUnpublishedAt();
                    return oldest == null ? 0 : Math.max(0,
                            Duration.between(oldest, clock.instant()).toSeconds());
                }).description("Age of oldest unpublished job event")
                .baseUnit("seconds").register(registry);
        for (IngestionRequestStatus status : IngestionRequestStatus.values()) {
            Gauge.builder("jobpulse.ingestion.requests", requests,
                            repo -> repo.countByStatus(status))
                    .tag("status", status.name().toLowerCase())
                    .description("Ingestion requests by lifecycle state")
                    .register(registry);
        }
    }

    public Timer.Sample startIngestion() { return Timer.start(registry); }

    public void finishIngestion(Timer.Sample sample, String provider,
            boolean success) {
        sample.stop(Timer.builder("jobpulse.ingestion.duration")
                .tag("provider", provider.toLowerCase())
                .tag("outcome", success ? "success" : "failure")
                .register(registry));
        Counter.builder("jobpulse.ingestion.completed")
                .tag("provider", provider.toLowerCase())
                .tag("outcome", success ? "success" : "failure")
                .register(registry).increment();
    }

    public void recordAlertLatency(Duration duration) {
        if (!duration.isNegative()) alertLatency.record(duration);
    }
}
