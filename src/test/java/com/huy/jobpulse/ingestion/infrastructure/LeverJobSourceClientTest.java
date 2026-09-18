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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LeverJobSourceClientTest {

    private static final String BASE_URL =
            "https://api.lever.co/v0/postings";

    private MockRestServiceServer server;
    private LeverJobSourceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LeverJobSourceClient(builder.build());
    }

    @Test
    void mapsLeverPostings() throws IOException {
        expectFixture("acme", "fixtures/lever-jobs.json");

        List<ExternalJob> jobs = client.fetchAll("acme");

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.sourceJobId()).isEqualTo("lever-101");
            assertThat(job.company()).isEqualTo("acme");
            assertThat(job.title()).isEqualTo("Platform Engineer");
            assertThat(job.location()).isEqualTo("Taipei");
            assertThat(job.employmentType()).isEqualTo("Full-time");
            assertThat(job.remotePolicy()).isEqualTo(RemotePolicy.HYBRID);
            assertThat(job.applyUrl()).endsWith("/apply");
        });
        server.verify();
    }

    @Test
    void acceptsAnEmptyBoardAsValid() {
        server.expect(once(), requestTo(url("empty")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.verify("empty"))
                .isEqualTo(BoardVerification.verified());
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
    void rejectsInvalidSiteBeforeCallingProvider() {
        assertThatThrownBy(() -> client.fetchAll("../private"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lever site");
        server.verify();
    }

    private void expectFixture(String site, String fixture) throws IOException {
        String body = new ClassPathResource(fixture)
                .getContentAsString(StandardCharsets.UTF_8);
        server.expect(once(), requestTo(url(site)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String url(String site) {
        return BASE_URL + "/" + site + "?mode=json";
    }
}
