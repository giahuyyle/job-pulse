package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.discovery.application.BoardVerification;
import com.huy.jobpulse.ingestion.application.ExternalJob;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AshbyJobSourceClientTest {

    private static final String BASE_URL =
            "https://api.ashbyhq.com/posting-api/job-board";

    private MockRestServiceServer server;
    private AshbyJobSourceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AshbyJobSourceClient(builder.build());
    }

    @Test
    void mapsListedJobsAndUsesUrlPathsAsStableIds() throws IOException {
        expectFixture("openai", "fixtures/ashby-jobs.json");

        List<ExternalJob> jobs = client.fetchAll("openai", "OpenAI");

        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(ExternalJob::sourceJobId)
                .containsExactly(
                        "openai/11111111-1111-1111-1111-111111111111",
                        "openai/22222222-2222-2222-2222-222222222222"
                );
        assertThat(jobs.getFirst()).satisfies(job -> {
            assertThat(job.company()).isEqualTo("OpenAI");
            assertThat(job.title()).isEqualTo("Research Engineer");
            assertThat(job.location()).isEqualTo("San Francisco");
            assertThat(job.description()).isEqualTo("Build research systems");
            assertThat(job.employmentType()).isEqualTo("FullTime");
            assertThat(job.remotePolicy()).isEqualTo(RemotePolicy.HYBRID);
            assertThat(job.postedAt()).isEqualTo(
                    Instant.parse("2026-09-01T12:00:00Z")
            );
        });
        assertThat(jobs.get(1).remotePolicy()).isEqualTo(RemotePolicy.REMOTE);
        server.verify();
    }

    @Test
    void acceptsEmptyJobsArrayAsAValidBoard() {
        expectJson("empty", "{\"jobs\":[]}");

        assertThat(client.verify("empty"))
                .isEqualTo(BoardVerification.verified());
        server.verify();
    }

    @Test
    void rejectsMissingJobsArray() {
        expectJson("broken", "{}");

        assertThatThrownBy(() -> client.fetchAll("broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jobs array");
        server.verify();
    }

    @Test
    void rejectsMalformedJobUrlAndInvalidDate() {
        expectJson("bad-url", response("relative", "2026-09-01T12:00:00Z"));
        expectJson("bad-date", response(
                "https://jobs.ashbyhq.com/acme/job-1",
                "not-a-date"
        ));

        assertThatThrownBy(() -> client.fetchAll("bad-url"))
                .hasMessageContaining("malformed jobUrl");
        assertThatThrownBy(() -> client.fetchAll("bad-date"))
                .hasMessageContaining("invalid publishedAt");
        server.verify();
    }

    @Test
    void treatsNotFoundAsUnverified() {
        server.expect(once(), requestTo(url("missing")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.verify("missing").valid()).isFalse();
        server.verify();
    }

    @Test
    void rejectsInvalidBoardNameBeforeCallingProvider() {
        assertThatThrownBy(() -> client.fetchAll("../private"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ashby board name");
        server.verify();
    }

    private void expectFixture(String board, String fixture) throws IOException {
        String body = new ClassPathResource(fixture)
                .getContentAsString(StandardCharsets.UTF_8);
        expectJson(board, body);
    }

    private void expectJson(String board, String body) {
        server.expect(once(), requestTo(url(board)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String url(String board) {
        return BASE_URL + "/" + board;
    }

    private static String response(String jobUrl, String publishedAt) {
        return """
                {"jobs":[{
                  "title":"Engineer",
                  "jobUrl":"%s",
                  "applyUrl":"https://example.com/apply",
                  "publishedAt":"%s",
                  "isListed":true
                }]}
                """.formatted(jobUrl, publishedAt);
    }
}
