package com.huy.jobpulse.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JobPulseMetricsTest {

    @Test
    void recordsBoundedSearchIngestionOutboxAndDeadLetterMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobPulseMetrics metrics = new JobPulseMetrics(registry);

        var sample = metrics.startTimer();
        metrics.finishIngestion(sample, "GREENHOUSE", "success");
        metrics.recordPostings("GREENHOUSE", "created", 3);
        metrics.recordSearch(2_000_000, "success");
        metrics.recordDeadLetter("ingestion");
        metrics.recordOutboxPublication("failed");
        metrics.recordAlertLatency(Duration.ofMillis(10));

        assertThat(registry.get("jobpulse.ingestion.runs")
                .tags("provider", "greenhouse", "outcome", "success")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("jobpulse.ingestion.postings")
                .tags("provider", "greenhouse", "outcome", "created")
                .counter().count()).isEqualTo(3);
        assertThat(registry.get("jobpulse.search.duration")
                .tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("jobpulse.messages.dead_lettered")
                .tag("queue", "ingestion").counter().count()).isEqualTo(1);
        assertThat(registry.get("jobpulse.outbox.publications")
                .tag("outcome", "failed").counter().count()).isEqualTo(1);

        Set<String> forbidden = Set.of("jobId", "runId", "boardId", "company",
                "query", "exception", "applyUrl");
        assertThat(registry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContainAnyElementsOf(forbidden);
    }
}
