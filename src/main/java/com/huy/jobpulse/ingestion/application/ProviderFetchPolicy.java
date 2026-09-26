package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.error.PermanentProviderException;
import com.huy.jobpulse.ingestion.error.ProviderRateLimitException;
import com.huy.jobpulse.ingestion.error.TransientProviderException;
import com.huy.jobpulse.jobs.domain.JobSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProviderFetchPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderFetchPolicy.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private final EnumMap<JobSource, Circuit> circuits = new EnumMap<>(JobSource.class);
    private final MeterRegistry metrics;
    private final Clock clock;

    public ProviderFetchPolicy(MeterRegistry metrics, Clock clock) {
        this.metrics = metrics;
        this.clock = clock;
        for (JobSource source : JobSource.values()) {
            Circuit circuit = new Circuit();
            circuits.put(source, circuit);
            for (var state : List.of("closed", "open", "half_open")) {
                int stateCode = switch (state) {
                    case "open" -> 1;
                    case "half_open" -> 2;
                    default -> 0;
                };
                Gauge.builder("jobpulse.circuit.state", circuit.state,
                        value -> value.get() == stateCode ? 1 : 0)
                        .tag("provider", tag(source)).tag("state", state)
                        .description("1 while provider circuit is in this state")
                        .register(metrics);
            }
        }
    }

    public List<ExternalJob> fetch(JobSource source, String account,
            Supplier<List<ExternalJob>> operation) {
        Circuit circuit = circuits.get(source);
        if (!circuit.acquire(source, clock.instant())) {
            metrics.counter("jobpulse.provider.requests", "provider", tag(source),
                    "outcome", "circuit_open").increment();
            throw new TransientProviderException("Provider circuit is open: " + source, null);
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1 && circuit.isOpen(clock.instant())) {
                metrics.counter("jobpulse.provider.requests", "provider", tag(source),
                        "outcome", "circuit_open").increment();
                throw new TransientProviderException("Provider circuit is open: " + source, null);
            }
            try {
                List<ExternalJob> result = operation.get();
                circuit.success(source);
                metrics.counter("jobpulse.provider.requests", "provider", tag(source),
                        "outcome", "success").increment();
                return result;
            } catch (RuntimeException raw) {
                RuntimeException failure = classify(raw);
                boolean transientFailure = failure instanceof TransientProviderException;
                if (raw instanceof ResourceAccessException) {
                    metrics.counter("jobpulse.provider.timeouts", "provider", tag(source)).increment();
                }
                metrics.counter("jobpulse.provider.requests", "provider", tag(source),
                        "outcome", transientFailure ? "transient_failure" : "permanent_failure").increment();
                circuit.failure(source, transientFailure, clock.instant());
                if (!transientFailure || attempt == MAX_ATTEMPTS) throw failure;
                Duration delay = delay(failure, attempt);
                metrics.counter("jobpulse.provider.retries", "provider", tag(source),
                        "reason", failure instanceof ProviderRateLimitException ? "rate_limit" : "transient").increment();
                LOGGER.atWarn().addKeyValue("provider", tag(source))
                        .addKeyValue("sourceAccount", account)
                        .addKeyValue("attempt", attempt)
                        .addKeyValue("delayMs", delay.toMillis())
                        .addKeyValue("cause", failure.getClass().getSimpleName())
                        .log("Provider fetch will retry");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new TransientProviderException("Provider retry interrupted", interrupted);
                }
            }
        }
        throw new IllegalStateException("Unreachable provider attempt count");
    }

    static RuntimeException classify(RuntimeException failure) {
        if (failure instanceof com.huy.jobpulse.ingestion.error.ProviderException) return failure;
        if (failure instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            if (status == 429) return new ProviderRateLimitException(
                    "Provider rate limited (HTTP 429)", retryAfter(http), http);
            if (status >= 500) return new TransientProviderException(
                    "Provider returned HTTP " + status, http);
            return new PermanentProviderException("Provider returned HTTP " + status, http);
        }
        if (failure instanceof ResourceAccessException) {
            return new TransientProviderException("Provider connection or timeout failure", failure);
        }
        if (failure instanceof RestClientException || failure instanceof IllegalStateException) {
            return new PermanentProviderException("Invalid provider response: " + failure.getMessage(), failure);
        }
        return failure;
    }

    private static Duration retryAfter(HttpStatusCodeException http) {
        String value = http.getResponseHeaders() == null ? null
                : http.getResponseHeaders().getFirst("Retry-After");
        if (value == null) return Duration.ZERO;
        try {
            return Duration.ofSeconds(Long.parseLong(value.strip()));
        } catch (NumberFormatException ignored) {
            try {
                return Duration.between(Instant.now(), ZonedDateTime.parse(value,
                        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (RuntimeException invalid) {
                return Duration.ZERO;
            }
        }
    }

    private static Duration delay(RuntimeException failure, int attempt) {
        long base = Math.min(MAX_DELAY.toMillis(), 500L << (attempt - 1));
        long jittered = Math.min(MAX_DELAY.toMillis(),
                (long) (base * ThreadLocalRandom.current().nextDouble(0.75, 1.25)));
        Duration retryAfter = failure instanceof ProviderRateLimitException rate
                ? rate.retryAfter() : Duration.ZERO;
        if (retryAfter.compareTo(MAX_DELAY) > 0) {
            throw new ProviderRateLimitException("Retry-After exceeds worker retry budget",
                    retryAfter, failure);
        }
        return Duration.ofMillis(Math.max(jittered, Math.max(0, retryAfter.toMillis())));
    }

    private static String tag(JobSource source) { return source.name().toLowerCase(); }

    private final class Circuit {
        private final boolean[] window = new boolean[20];
        private int calls;
        private int failures;
        private int cursor;
        private Instant openUntil;
        private int probes;
        private int successfulProbes;
        private final AtomicInteger state = new AtomicInteger();

        synchronized boolean acquire(JobSource source, Instant now) {
            if (state.get() == 0) return true;
            if (state.get() == 1) {
                if (now.isBefore(openUntil)) return false;
                state.set(2);
                probes = 0;
                successfulProbes = 0;
                LOGGER.atWarn().addKeyValue("provider", tag(source))
                        .addKeyValue("state", "half_open")
                        .log("Provider circuit changed state");
            }
            if (probes >= 3) return false;
            probes++;
            return true;
        }

        synchronized boolean isOpen(Instant now) {
            return state.get() == 1 && now.isBefore(openUntil);
        }

        synchronized void success(JobSource source) {
            if (state.get() == 2) {
                successfulProbes++;
                if (successfulProbes >= 3) transition(source, null);
                return;
            }
            if (state.get() == 1) return;
            count(false, source, clock.instant());
        }

        synchronized void failure(JobSource source, boolean transientFailure, Instant now) {
            if (state.get() == 2 && !transientFailure) {
                successfulProbes++;
                if (successfulProbes >= 3) transition(source, null);
                return;
            }
            if ((state.get() == 2 || state.get() == 1) && transientFailure) {
                transition(source, now.plusSeconds(30));
                return;
            }
            if (state.get() == 1) return;
            count(transientFailure, source, now);
        }

        private void count(boolean failed, JobSource source, Instant now) {
            if (calls == window.length && window[cursor]) failures--;
            window[cursor] = failed;
            cursor = (cursor + 1) % window.length;
            calls = Math.min(window.length, calls + 1);
            if (failed) failures++;
            if (calls >= 10 && failures * 2 >= calls) transition(source, now.plusSeconds(30));
        }

        private void transition(JobSource source, Instant until) {
            openUntil = until;
            state.set(until == null ? 0 : 1);
            probes = 0;
            successfulProbes = 0;
            calls = 0;
            failures = 0;
            cursor = 0;
            LOGGER.atWarn().addKeyValue("provider", tag(source))
                    .addKeyValue("state", until == null ? "closed" : "open")
                    .log("Provider circuit changed state");
        }
    }
}
