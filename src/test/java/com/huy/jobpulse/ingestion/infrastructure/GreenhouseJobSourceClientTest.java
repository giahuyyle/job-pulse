package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.application.ExternalJob;
import com.huy.jobpulse.discovery.application.BoardVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GreenhouseJobSourceClientTest {

    private MockRestServiceServer server;
    private GreenhouseJobSourceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://boards-api.greenhouse.io/v1/boards");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GreenhouseJobSourceClient(builder.build());
    }

    @Test
    void mapsBoardAndJobsResponses() throws IOException {
        expectJson("/example", "fixtures/greenhouse-board.json");
        expectJson(
                "/example/jobs?content=true",
                "fixtures/greenhouse-jobs.json"
        );

        List<ExternalJob> jobs = client.fetchAll("example");

        assertThat(jobs).hasSize(2);
        assertThat(jobs.getFirst())
                .extracting(
                        ExternalJob::sourceJobId,
                        ExternalJob::company,
                        ExternalJob::title,
                        ExternalJob::location,
                        ExternalJob::description,
                        ExternalJob::applyUrl
                )
                .containsExactly(
                        "101",
                        "Example Company",
                        "Backend Engineer",
                        "Taipei",
                        "Build reliable APIs",
                        "https://boards.greenhouse.io/example/jobs/101"
                );
        assertThat(jobs.get(1).location()).isNull();
        server.verify();
    }

    @Test
    void cachesBoardNameAcrossFetches() throws IOException {
        expectJson("/example", "fixtures/greenhouse-board.json");
        expectJson(
                "/example/jobs?content=true",
                "fixtures/greenhouse-jobs.json"
        );
        expectJson(
                "/example/jobs?content=true",
                "fixtures/greenhouse-jobs.json"
        );

        client.fetchAll("example");
        client.fetchAll("example");

        server.verify();
    }

    @Test
    void verifiesBoardWithoutFetchingJobs() throws IOException {
        expectJson("/example", "fixtures/greenhouse-board.json");

        assertThat(client.verify("example"))
                .isEqualTo(BoardVerification.verified());
        server.verify();
    }

    @Test
    void verificationRechecksProviderWhenNameIsCached() throws IOException {
        expectJson("/example", "fixtures/greenhouse-board.json");
        expectJson(
                "/example/jobs?content=true",
                "fixtures/greenhouse-jobs.json"
        );
        expectJson("/example", "fixtures/greenhouse-board.json");

        client.fetchAll("example");
        client.verify("example");

        server.verify();
    }

    @Test
    void treatsMissingBoardAsUnverified() {
        server.expect(once(), requestTo(url("/missing")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.verify("missing").valid()).isFalse();
        server.verify();
    }

    @Test
    void rejectsResponseWithMissingJobsArray() {
        server.expect(once(), requestTo(url("/example")))
                .andRespond(withSuccess(
                        "{\"name\":\"Example Company\"}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(url("/example/jobs?content=true")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchAll("example"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jobs array");
        server.verify();
    }

    @Test
    void propagatesHttpErrors() {
        server.expect(once(), requestTo(url("/missing")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchAll("missing"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        server.verify();
    }

    @Test
    void rejectsInvalidBoardTokenBeforeMakingARequest() {
        assertThatThrownBy(() -> client.fetchAll("../other-host"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("board token");
        server.verify();
    }

    private void expectJson(String path, String fixture) throws IOException {
        String body = new ClassPathResource(fixture)
                .getContentAsString(StandardCharsets.UTF_8);
        server.expect(once(), requestTo(url(path)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String url(String path) {
        return "https://boards-api.greenhouse.io/v1/boards" + path;
    }
}
