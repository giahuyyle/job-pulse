package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "jobpulse.ingestion.initial-delay-ms=3600000")
@Testcontainers
@Import(IngestionTargetSchedulerTest.MutableClockConfiguration.class)
class IngestionTargetSchedulerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired IngestionTargetService targetService;
    @Autowired IngestionRequestService requestService;
    @Autowired IngestionTargetRepository targetRepository;
    @Autowired IngestionRequestRepository requestRepository;
    @Autowired MutableClock clock;

    @BeforeEach
    void cleanDatabase() {
        requestRepository.deleteAll();
        targetRepository.deleteAll();
    }

    @Test
    void schedulerCreatesOneRequestAndAdvancesDueTargetAtomically() {
        IngestionTarget due = register("board-a", "Company A", 60);
        IngestionTarget disabled = register("board-b", "Company B", 60);
        targetService.setEnabled(disabled.getId(), false);
        clock.advance(Duration.ofMinutes(3));

        IngestionTargetScheduler scheduler = new IngestionTargetScheduler(requestService, clock);
        scheduler.pollDueTargets();
        scheduler.pollDueTargets();

        assertThat(requestRepository.findAll()).singleElement().satisfies(request -> {
            assertThat(request.getIngestionTargetId()).isEqualTo(due.getId());
            assertThat(request.getStatus()).isEqualTo(IngestionRequestStatus.PENDING);
        });
        assertThat(targetRepository.findById(due.getId()).orElseThrow().getNextRunAt())
                .isAfter(clock.instant());
        assertThat(targetRepository.findById(disabled.getId()).orElseThrow().getLastSuccessAt())
                .isNull();
    }

    @Test
    void manualRequestsReuseTheOutstandingRequest() {
        register("same-board", "Company", 60);

        var first = requestService.request(JobSource.GREENHOUSE, "same-board");
        var second = requestService.request(JobSource.GREENHOUSE, "same-board");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(requestRepository.count()).isOne();
    }

    @Test
    void rejectsDuplicateProviderAndBoardIdentity() {
        register("duplicate-board", "First Company", 60);
        assertThatThrownBy(() -> register("duplicate-board", "Second Company", 60))
                .isInstanceOf(DuplicateIngestionTargetException.class);
    }

    private IngestionTarget register(String account, String company, Integer interval) {
        return targetService.create(new CreateIngestionTargetRequest(
                JobSource.GREENHOUSE,
                account,
                company,
                "https://example.com/careers/" + account,
                interval
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
