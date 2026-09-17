package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobSource;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "jobpulse.ingestion.initial-delay-ms=3600000")
@Testcontainers
@Import(IngestionTargetSchedulerTest.MutableClockConfiguration.class)
class IngestionTargetSchedulerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    IngestionTargetService targetService;

    @Autowired
    IngestionTargetRepository repository;

    @Autowired
    MutableClock clock;

    @Test
    void pollsDueTargetsAndIsolatesDisabledFailedAndOverlappingBoards() {
        IngestionTarget successful = register("board-a", "Company A", 60);
        IngestionTarget failing = register("board-b", "Company B", 60);
        IngestionTarget disabled = register("board-c", "Company C", null);
        IngestionTarget overlapping = register(
                "board-overlap",
                "Company D",
                60
        );
        targetService.setEnabled(disabled.getId(), false);

        clock.advance(Duration.ofMinutes(3));
        RecordingCoordinator coordinator = new RecordingCoordinator();
        coordinator.fail("board-b");
        coordinator.overlap("board-overlap");
        IngestionTargetScheduler scheduler = new IngestionTargetScheduler(
                targetService,
                coordinator,
                clock
        );

        scheduler.pollDueTargets();

        IngestionTarget successfulAfter = reload(successful);
        IngestionTarget failingAfter = reload(failing);
        IngestionTarget disabledAfter = reload(disabled);
        IngestionTarget overlappingAfter = reload(overlapping);

        assertThat(successfulAfter.getLastSuccessAt())
                .isEqualTo(clock.instant());
        assertThat(successfulAfter.getLastError()).isNull();
        assertThat(successfulAfter.getNextRunAt()).isAfter(clock.instant());

        assertThat(failingAfter.getLastSuccessAt()).isNull();
        assertThat(failingAfter.getLastError())
                .contains("provider unavailable");
        assertThat(failingAfter.getNextRunAt()).isAfter(clock.instant());

        assertThat(disabledAfter.isEnabled()).isFalse();
        assertThat(disabledAfter.getLastSuccessAt()).isNull();
        assertThat(coordinator.callsFor("board-c")).isZero();

        assertThat(overlappingAfter.getLastSuccessAt()).isNull();
        assertThat(overlappingAfter.getLastError()).isNull();
        assertThat(overlappingAfter.getNextRunAt())
                .isBeforeOrEqualTo(clock.instant());

        scheduler.pollDueTargets();

        assertThat(coordinator.callsFor("board-a")).isEqualTo(1);
        assertThat(coordinator.callsFor("board-b")).isEqualTo(1);
        assertThat(coordinator.callsFor("board-overlap")).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateProviderAndBoardIdentity() {
        register("duplicate-board", "First Company", 60);

        assertThatThrownBy(() -> register(
                "duplicate-board",
                "Second Company",
                60
        )).isInstanceOf(DuplicateIngestionTargetException.class);
    }

    private IngestionTarget register(
            String sourceAccount,
            String company,
            Integer intervalMinutes
    ) {
        return targetService.create(new CreateIngestionTargetRequest(
                JobSource.GREENHOUSE,
                sourceAccount,
                company,
                "https://example.com/careers/" + sourceAccount,
                intervalMinutes
        ));
    }

    private IngestionTarget reload(IngestionTarget target) {
        return repository.findById(target.getId()).orElseThrow();
    }

    private static class RecordingCoordinator
            implements IngestionCoordinator {

        private final List<String> failures = new ArrayList<>();
        private final List<String> overlaps = new ArrayList<>();
        private final Map<String, Integer> calls = new HashMap<>();

        @Override
        public IngestionResult ingest(
                JobSource source,
                String sourceAccount
        ) {
            calls.merge(sourceAccount, 1, Integer::sum);
            if (overlaps.contains(sourceAccount)) {
                throw new IngestionAlreadyRunningException("already running");
            }
            if (failures.contains(sourceAccount)) {
                throw new IllegalStateException("provider unavailable");
            }
            return new IngestionResult(1, 1, 0, 0, 0);
        }

        void fail(String sourceAccount) {
            failures.add(sourceAccount);
        }

        void overlap(String sourceAccount) {
            overlaps.add(sourceAccount);
        }

        int callsFor(String sourceAccount) {
            return calls.getOrDefault(sourceAccount, 0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(
                    Instant.parse("2026-09-01T12:00:00Z")
            );
        }
    }

    static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
