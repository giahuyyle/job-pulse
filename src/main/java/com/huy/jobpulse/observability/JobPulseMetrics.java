package com.huy.jobpulse.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class JobPulseMetrics {

    private final MeterRegistry registry;
    private final Timer alertLatency;

    public JobPulseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.alertLatency = Timer.builder("jobpulse.alert.delivery")
                .description("Time from job CREATED event to in-app alert")
                .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void finishIngestion(Timer.Sample sample, String provider, String outcome) {
        String normalizedProvider = normalize(provider);
        String normalizedOutcome = normalize(outcome);
        sample.stop(Timer.builder("jobpulse.ingestion.duration")
                .description("Duration of a board ingestion run")
                .tag("provider", normalizedProvider)
                .tag("outcome", normalizedOutcome)
                .register(registry));
        registry.counter("jobpulse.ingestion.runs",
                "provider", normalizedProvider,
                "outcome", normalizedOutcome).increment();
    }

    public void recordPostings(String provider, String outcome, long count) {
        if (count <= 0) {
            return;
        }
        registry.counter("jobpulse.ingestion.postings",
                "provider", normalize(provider),
                "outcome", normalize(outcome)).increment(count);
    }

    public void recordSearch(long durationNanos, String outcome) {
        Timer.builder("jobpulse.search.duration")
                .description("Duration of a job search request")
                .tag("outcome", normalize(outcome))
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDeadLetter(String queue) {
        registry.counter("jobpulse.messages.dead_lettered",
                "queue", normalize(queue)).increment();
    }

    public void recordOutboxPublication(String outcome) {
        registry.counter("jobpulse.outbox.publications",
                "outcome", normalize(outcome)).increment();
    }

    public void recordAlertLatency(Duration duration) {
        if (!duration.isNegative()) {
            alertLatency.record(duration);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
