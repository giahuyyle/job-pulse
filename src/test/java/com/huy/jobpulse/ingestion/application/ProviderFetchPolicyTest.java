package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.error.PermanentProviderException;
import com.huy.jobpulse.ingestion.error.TransientProviderException;
import com.huy.jobpulse.jobs.domain.JobSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderFetchPolicyTest {
    @Test
    void retriesTwoTransientFailuresThenSucceeds() {
        var policy = new ProviderFetchPolicy(new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"), ZoneOffset.UTC));
        var calls = new AtomicInteger();
        assertThat(policy.fetch(JobSource.LEVER, "test", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
            }
            return List.of();
        })).isEmpty();
        assertThat(calls).hasValue(3);
    }

    @Test
    void permanentUnauthorizedResponseIsNotRetried() {
        var policy = new ProviderFetchPolicy(new SimpleMeterRegistry(), Clock.systemUTC());
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> policy.fetch(JobSource.ASHBY, "test", () -> {
            calls.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        })).isInstanceOf(PermanentProviderException.class)
                .hasMessageContaining("401");
        assertThat(calls).hasValue(1);
    }

    @Test
    void longRateLimitDelayDoesNotHoldWorker() {
        var policy = new ProviderFetchPolicy(new SimpleMeterRegistry(), Clock.systemUTC());
        var calls = new AtomicInteger();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "120");
        assertThatThrownBy(() -> policy.fetch(JobSource.LEVER, "test", () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                    "rate limited", headers, new byte[0], null);
        })).hasMessageContaining("Retry-After exceeds worker retry budget");
        assertThat(calls).hasValue(1);
    }

    @Test
    void openCircuitIsLimitedToOneProviderAndRecoversAfterWait() {
        var clock = new MutableClock();
        var policy = new ProviderFetchPolicy(new SimpleMeterRegistry(), clock);
        var calls = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> policy.fetch(JobSource.GREENHOUSE, "test", () -> {
                calls.incrementAndGet();
                throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
            })).isInstanceOf(TransientProviderException.class);
        }
        int beforeOpenCall = calls.get();
        assertThatThrownBy(() -> policy.fetch(JobSource.GREENHOUSE, "test", () -> {
            calls.incrementAndGet();
            return List.of();
        })).hasMessageContaining("circuit is open");
        assertThat(calls).hasValue(beforeOpenCall);
        assertThat(policy.fetch(JobSource.LEVER, "test", List::of)).isEmpty();
        clock.advanceSeconds(31);
        for (int i = 0; i < 3; i++) {
            assertThat(policy.fetch(JobSource.GREENHOUSE, "test", List::of)).isEmpty();
        }
        assertThat(policy.fetch(JobSource.GREENHOUSE, "test", List::of)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-26T00:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
