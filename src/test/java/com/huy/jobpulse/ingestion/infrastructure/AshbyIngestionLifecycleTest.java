package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.application.IngestionResult;
import com.huy.jobpulse.ingestion.application.IngestionService;
import com.huy.jobpulse.ingestion.application.IngestionWriter;
import com.huy.jobpulse.ingestion.application.JobSourceRegistry;
import com.huy.jobpulse.ingestion.application.RunRecorder;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(properties = {
        "jobpulse.ingestion.initial-delay-ms=3600000",
        "jobpulse.discovery.initial-delay-ms=3600000"
})
@Testcontainers
class AshbyIngestionLifecycleTest {

    private static final String BASE_URL =
            "https://api.ashbyhq.com/posting-api/job-board";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    IngestionWriter writer;

    @Autowired
    RunRecorder runRecorder;

    @Autowired
    JobPostingRepository postingRepository;

    @Autowired
    IngestionRunRepository runRepository;

    private MockRestServiceServer server;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        runRepository.deleteAll();
        postingRepository.deleteAll();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        AshbyJobSourceClient client = new AshbyJobSourceClient(builder.build());
        service = new IngestionService(
                new JobSourceRegistry(List.of(client)),
                writer,
                runRecorder
        );
    }

    @Test
    void updatesByStableIdClosesOnValidEmptyAndPreservesOnMalformed()
            throws IOException {
        String initial = fixture("fixtures/ashby-jobs.json");
        String updated = initial.replace(
                "Research Engineer",
                "Senior Research Engineer"
        );
        expect(initial);
        expect(updated);
        expect("{}");
        expect("{\"jobs\":[]}");
        expect("{\"jobs\":[]}");

        IngestionResult first = ingest();
        assertThat(first).isEqualTo(new IngestionResult(2, 2, 0, 0, 0));
        assertThat(activePostings())
                .allSatisfy(posting -> assertThat(posting.getCompany())
                        .isEqualTo("OpenAI"));

        IngestionResult second = ingest();
        assertThat(second).isEqualTo(new IngestionResult(2, 0, 1, 1, 0));
        assertThat(postingRepository.countBySourceAndSourceAccount(
                JobSource.ASHBY,
                "openai"
        )).isEqualTo(2);

        assertThatThrownBy(this::ingest)
                .hasMessageContaining("jobs array");
        assertThat(activePostings()).hasSize(2)
                .allSatisfy(posting -> assertThat(
                        posting.getConsecutiveMissingRuns()
                ).isZero());

        assertThat(ingest().closed())
                .isZero();
        assertThat(activePostings()).hasSize(2)
                .allSatisfy(posting -> assertThat(
                        posting.getConsecutiveMissingRuns()
                ).isEqualTo(1));

        assertThat(ingest().closed())
                .isEqualTo(2);
        assertThat(activePostings()).isEmpty();

        assertThat(runRepository
                .findAllBySourceAndSourceAccountOrderByStartedAtAsc(
                        JobSource.ASHBY,
                        "openai"
                ))
                .extracting(run -> run.getStatus())
                .containsExactly(
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.FAILED,
                        IngestionRunStatus.SUCCEEDED,
                        IngestionRunStatus.SUCCEEDED
                );
        server.verify();
    }

    private List<JobPosting> activePostings() {
        return postingRepository.findAllBySourceAndSourceAccountAndStatus(
                JobSource.ASHBY,
                "openai",
                JobStatus.ACTIVE
        );
    }

    private IngestionResult ingest() {
        return service.ingest(JobSource.ASHBY, "openai", "OpenAI");
    }

    private void expect(String body) {
        server.expect(once(), requestTo(BASE_URL + "/openai"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String fixture(String path) throws IOException {
        return new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
