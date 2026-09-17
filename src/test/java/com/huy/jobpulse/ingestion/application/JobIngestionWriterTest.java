package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
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
import java.time.Instant;
import java.time.ZoneOffset;
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

    @Test
    void createsLeavesUnchangedAndUpdatesBySourceIdentity() {
        List<ExternalJob> initialJobs = List.of(
                posting("101", "Build APIs"),
                posting("102", "Improve infrastructure")
        );

        IngestionResult first = writer.upsert(
                JobSource.GREENHOUSE,
                "example",
                initialJobs
        );
        UUID originalId = find("101").getId();

        assertThat(first).isEqualTo(new IngestionResult(2, 2, 0, 0));
        assertThat(repository.count()).isEqualTo(2);

        IngestionResult second = writer.upsert(
                JobSource.GREENHOUSE,
                "example",
                initialJobs
        );

        assertThat(second).isEqualTo(new IngestionResult(2, 0, 0, 2));
        assertThat(repository.count()).isEqualTo(2);

        IngestionResult third = writer.upsert(
                JobSource.GREENHOUSE,
                "example",
                List.of(
                        posting("101", "Build distributed APIs"),
                        posting("102", "Improve infrastructure")
                )
        );
        JobPosting updated = find("101");

        assertThat(third).isEqualTo(new IngestionResult(2, 0, 1, 1));
        assertThat(repository.count()).isEqualTo(2);
        assertThat(updated.getId()).isEqualTo(originalId);
        assertThat(updated.getDescription()).isEqualTo("Build distributed APIs");

        IngestionResult fourth = writer.upsert(
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

        assertThat(fourth).isEqualTo(new IngestionResult(2, 0, 1, 1));
        assertThat(find("101").getCompany()).isEqualTo("Renamed Company");
        assertThat(find("101").getId()).isEqualTo(originalId);
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

        assertThatThrownBy(() -> writer.upsert(
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

    private JobPosting find(String sourceJobId) {
        return repository.findBySourceAndSourceAccountAndSourceJobId(
                        JobSource.GREENHOUSE,
                        "example",
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-01T12:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
