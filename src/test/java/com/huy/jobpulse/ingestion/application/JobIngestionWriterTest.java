package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import(JobIngestionWriterTest.FixedClockConfiguration.class)
class JobIngestionWriterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    JobIngestionWriter writer;

    @Autowired
    JobPostingRepository repository;

    @Autowired
    JobEventRepository eventRepository;

    @Autowired
    RunRecorder runRecorder;

    @Autowired
    IngestionRunRepository runRepository;

    @Autowired
    MutableClock clock;

    @Test
    void closesAfterTwoSuccessfulMissesAndReopensTheSamePosting() {
        long closedBefore = eventRepository.countByEventType(
                JobEventType.CLOSED
        );
        long updatedBefore = eventRepository.countByEventType(
                JobEventType.UPDATED
        );
        MutableJobSourceClient client = new MutableJobSourceClient();
        IngestionService service = new IngestionService(
                new JobSourceRegistry(List.of(client)),
                writer,
                runRecorder
        );
        String account = "lifecycle-test";

        client.returnJobs(List.of(
                posting("a", "Job A"),
                posting("b", "Job B")
        ));
        assertThat(service.ingest(JobSource.GREENHOUSE, account))
                .isEqualTo(new IngestionResult(2, 2, 0, 0, 0));
        JobPosting originalB = find(account, "b");
        UUID originalId = originalB.getId();
        Instant originalLastSeenAt = originalB.getLastSeenAt();

        clock.advance(Duration.ofHours(1));
        client.returnJobs(List.of(posting("a", "Job A")));
        assertThat(service.ingest(JobSource.GREENHOUSE, account).closed())
                .isZero();
        JobPosting missingOnce = find(account, "b");
        assertThat(missingOnce.getStatus()).isEqualTo(JobStatus.ACTIVE);
        assertThat(missingOnce.getConsecutiveMissingRuns()).isEqualTo(1);
        assertThat(missingOnce.getLastSeenAt()).isEqualTo(originalLastSeenAt);

        clock.advance(Duration.ofHours(1));
        client.failWith(new IllegalStateException("upstream unavailable"));
        assertThatThrownBy(() -> service.ingest(
                JobSource.GREENHOUSE,
                account
        )).hasMessage("upstream unavailable");
        assertMissingOnce(account, originalLastSeenAt);

        clock.advance(Duration.ofHours(1));
        client.returnJobs(List.of(new ExternalJob(
                "a",
                "Example Company",
                " ",
                "Remote",
                "Malformed",
                null,
                RemotePolicy.UNSPECIFIED,
                "https://example.com/a",
                null
        )));
        assertThatThrownBy(() -> service.ingest(
                JobSource.GREENHOUSE,
                account
        )).hasMessageContaining("invalid posting");
        assertMissingOnce(account, originalLastSeenAt);

        clock.advance(Duration.ofHours(1));
        client.returnJobs(List.of());
        IngestionResult emptySnapshot = service.ingest(
                JobSource.GREENHOUSE,
                account
        );
        assertThat(emptySnapshot.closed()).isEqualTo(1);
        assertThat(find(account, "b").getStatus())
                .isEqualTo(JobStatus.CLOSED);
        assertThat(eventRepository.countByEventType(JobEventType.CLOSED))
                .isEqualTo(closedBefore + 1);
        Instant closedAt = clock.instant();

        clock.advance(Duration.ofHours(1));
        client.returnJobs(List.of(posting("a", "Job A")));
        IngestionResult closingRun = service.ingest(
                JobSource.GREENHOUSE,
                account
        );
        JobPosting closed = find(account, "b");
        assertThat(closingRun.closed()).isZero();
        assertThat(closed.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(closed.getConsecutiveMissingRuns()).isEqualTo(2);
        assertThat(closed.getClosedAt()).isEqualTo(closedAt);
        assertThat(closed.getLastSeenAt()).isEqualTo(originalLastSeenAt);

        clock.advance(Duration.ofHours(1));
        client.returnJobs(List.of(
                posting("a", "Job A"),
                posting("b", "Job B")
        ));
        IngestionResult reopeningRun = service.ingest(
                JobSource.GREENHOUSE,
                account
        );
        JobPosting reopened = find(account, "b");
        assertThat(reopeningRun.updated()).isEqualTo(1);
        assertThat(reopened.getId()).isEqualTo(originalId);
        assertThat(reopened.getStatus()).isEqualTo(JobStatus.ACTIVE);
        assertThat(reopened.getConsecutiveMissingRuns()).isZero();
        assertThat(reopened.getClosedAt()).isNull();
        assertThat(reopened.getLastSeenAt()).isEqualTo(clock.instant());
        assertThat(eventRepository.countByEventType(JobEventType.UPDATED))
                .isEqualTo(updatedBefore + 1);

        assertThat(runRepository
                .findAllBySourceAndSourceAccountOrderByStartedAtAsc(
                        JobSource.GREENHOUSE,
                        account
                ))
                .extracting(run -> run.getStatus())
                .containsExactly(
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.FAILED,
                        IngestionRunStatus.FAILED,
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.SUCCEEDED
                );
    }

    @Test
    void missingPostingsAreIsolatedByBoard() {
        MutableJobSourceClient client = new MutableJobSourceClient();
        IngestionService service = new IngestionService(
                new JobSourceRegistry(List.of(client)),
                writer,
                runRecorder
        );

        client.returnJobs(List.of(
                posting("x-a", "Board X A"),
                posting("x-b", "Board X B")
        ));
        service.ingest(JobSource.GREENHOUSE, "board-x");
        client.returnJobs(List.of(posting("y-a", "Board Y A")));
        service.ingest(JobSource.GREENHOUSE, "board-y");

        clock.advance(Duration.ofHours(1));
        client.returnJobs(List.of(posting("x-a", "Board X A")));
        service.ingest(JobSource.GREENHOUSE, "board-x");
        clock.advance(Duration.ofHours(1));
        service.ingest(JobSource.GREENHOUSE, "board-x");

        JobPosting boardYPosting = find("board-y", "y-a");
        assertThat(boardYPosting.getStatus()).isEqualTo(JobStatus.ACTIVE);
        assertThat(boardYPosting.getConsecutiveMissingRuns()).isZero();
        assertThat(boardYPosting.getClosedAt()).isNull();
    }

    @Test
    void failedWriterTransactionRollsBackJobsAndRecordsFailure() {
        long eventsBefore = eventRepository.count();
        MutableJobSourceClient client = new MutableJobSourceClient();
        IngestionService service = new IngestionService(
                new JobSourceRegistry(List.of(client)),
                writer,
                runRecorder
        );
        String account = "writer-failure-test";
        client.returnJobs(List.of(
                posting("valid", "Valid posting"),
                posting("invalid", "x".repeat(256), "Too-long company")
        ));

        assertThatThrownBy(() -> service.ingest(
                JobSource.GREENHOUSE,
                account
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countFor(account)).isZero();
        assertThat(eventRepository.count()).isEqualTo(eventsBefore);
        assertThat(runRepository
                .findAllBySourceAndSourceAccountOrderByStartedAtAsc(
                        JobSource.GREENHOUSE,
                        account
                ))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getStatus())
                            .isEqualTo(IngestionRunStatus.FAILED);
                    assertThat(run.getFailureMessage()).isNotBlank();
                });
    }

    @Test
    void createsLeavesUnchangedAndUpdatesBySourceIdentity() {
        long eventsBefore = eventRepository.count();
        long createdBefore = eventRepository.countByEventType(
                JobEventType.CREATED
        );
        long updatedBefore = eventRepository.countByEventType(
                JobEventType.UPDATED
        );
        List<ExternalJob> initialJobs = List.of(
                posting("101", "Build APIs"),
                posting("102", "Improve infrastructure")
        );

        IngestionResult first = apply(
                JobSource.GREENHOUSE,
                "example",
                initialJobs
        );
        UUID originalId = find("101").getId();

        assertThat(first).isEqualTo(new IngestionResult(2, 2, 0, 0, 0));
        assertThat(countFor("example")).isEqualTo(2);
        assertThat(eventRepository.countByEventType(JobEventType.CREATED))
                .isEqualTo(createdBefore + 2);

        IngestionResult second = apply(
                JobSource.GREENHOUSE,
                "example",
                initialJobs
        );

        assertThat(second).isEqualTo(new IngestionResult(2, 0, 0, 2, 0));
        assertThat(countFor("example")).isEqualTo(2);
        assertThat(eventRepository.count()).isEqualTo(eventsBefore + 2);

        IngestionResult third = apply(
                JobSource.GREENHOUSE,
                "example",
                List.of(
                        posting("101", "Build distributed APIs"),
                        posting("102", "Improve infrastructure")
                )
        );
        JobPosting updated = find("101");

        assertThat(third).isEqualTo(new IngestionResult(2, 0, 1, 1, 0));
        assertThat(countFor("example")).isEqualTo(2);
        assertThat(updated.getId()).isEqualTo(originalId);
        assertThat(updated.getDescription()).isEqualTo("Build distributed APIs");
        assertThat(eventRepository.countByEventType(JobEventType.UPDATED))
                .isEqualTo(updatedBefore + 1);

        IngestionResult fourth = apply(
                JobSource.GREENHOUSE,
                "example",
                List.of(
                        posting(
                                "101",
                                "Renamed Company",
                                "Build distributed APIs"
                        ),
                        posting("102", "Improve infrastructure")
                )
        );

        assertThat(fourth).isEqualTo(new IngestionResult(2, 0, 1, 1, 0));
        assertThat(find("101").getCompany()).isEqualTo("Renamed Company");
        assertThat(find("101").getId()).isEqualTo(originalId);
        assertThat(eventRepository.countByEventType(JobEventType.UPDATED))
                .isEqualTo(updatedBefore + 2);
    }

    @Test
    void rollsBackTheWholeBatchWhenOnePostingIsInvalid() {
        ExternalJob invalid = new ExternalJob(
                "202",
                "Example Company",
                " ",
                "Remote",
                "Invalid posting",
                null,
                RemotePolicy.UNSPECIFIED,
                "https://boards.greenhouse.io/example/jobs/202",
                null
        );

        assertThatThrownBy(() -> apply(
                JobSource.GREENHOUSE,
                "atomic-test",
                List.of(posting("201", "Valid posting"), invalid)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(repository
                .findBySourceAndSourceAccountAndSourceJobId(
                        JobSource.GREENHOUSE,
                        "atomic-test",
                        "201"
                ))
                .isEmpty();
    }

    private IngestionResult apply(
            JobSource source,
            String sourceAccount,
            List<ExternalJob> jobs
    ) {
        return writer.apply(
                runRecorder.start(source, sourceAccount),
                source,
                sourceAccount,
                jobs
        );
    }

    private JobPosting find(String sourceJobId) {
        return find("example", sourceJobId);
    }

    private long countFor(String account) {
        return repository.countBySourceAndSourceAccount(
                JobSource.GREENHOUSE,
                account
        );
    }

    private JobPosting find(String account, String sourceJobId) {
        return repository.findBySourceAndSourceAccountAndSourceJobId(
                        JobSource.GREENHOUSE,
                        account,
                        sourceJobId
                )
                .orElseThrow();
    }

    private ExternalJob posting(String sourceJobId, String description) {
        return posting(sourceJobId, "Example Company", description);
    }

    private ExternalJob posting(
            String sourceJobId,
            String company,
            String description
    ) {
        return new ExternalJob(
                sourceJobId,
                company,
                "Backend Engineer " + sourceJobId,
                "Remote",
                description,
                null,
                RemotePolicy.UNSPECIFIED,
                "https://boards.greenhouse.io/example/jobs/" + sourceJobId,
                null
        );
    }

    private void assertMissingOnce(
            String account,
            Instant expectedLastSeenAt
    ) {
        JobPosting posting = find(account, "b");
        assertThat(posting.getStatus()).isEqualTo(JobStatus.ACTIVE);
        assertThat(posting.getConsecutiveMissingRuns()).isEqualTo(1);
        assertThat(posting.getLastSeenAt()).isEqualTo(expectedLastSeenAt);
        assertThat(posting.getClosedAt()).isNull();
    }

    private static class MutableJobSourceClient implements JobSourceClient {

        private List<ExternalJob> jobs = List.of();
        private RuntimeException failure;

        @Override
        public JobSource source() {
            return JobSource.GREENHOUSE;
        }

        @Override
        public void validateSourceAccount(String sourceAccount) {
        }

        @Override
        public List<ExternalJob> fetchAll(String sourceAccount) {
            if (failure != null) {
                throw failure;
            }
            return jobs;
        }

        void returnJobs(List<ExternalJob> jobs) {
            this.jobs = jobs;
            this.failure = null;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock fixedClock() {
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
