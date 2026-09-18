package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoardCandidateExtractorTest {

    private final BoardCandidateExtractor extractor =
            new BoardCandidateExtractor();

    @Test
    void deduplicatesLinksForTheSameGreenhouseBoard() throws IOException {
        BoardCandidateExtraction result = extract(
                "https://acme.example/careers",
                "fixtures/discovery-greenhouse.html"
        );

        assertThat(result.candidates()).singleElement()
                .extracting(
                        BoardCandidate::source,
                        BoardCandidate::sourceAccount
                )
                .containsExactly(JobSource.GREENHOUSE, "acme");
    }

    @Test
    void extractsBoardFromFinalRedirectUrl() {
        BoardCandidateExtraction result = extractor.extract(
                new FetchedCareersPage(
                        URI.create("https://jobs.lever.co/acme/12345"),
                        "<html></html>"
                )
        );

        assertThat(result.candidates()).singleElement()
                .extracting(
                        BoardCandidate::source,
                        BoardCandidate::sourceAccount
                )
                .containsExactly(JobSource.LEVER, "acme");
    }

    @Test
    void matchesHostnamesRatherThanUrlText() throws IOException {
        BoardCandidateExtraction result = extract(
                "https://example.com/careers",
                "fixtures/discovery-malicious.html"
        );

        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void keepsEuropeanLeverHostForReview() {
        BoardCandidateExtraction result = extractor.extract(
                new FetchedCareersPage(
                        URI.create("https://example.com/careers"),
                        "<a href='https://jobs.eu.lever.co/acme'>Jobs</a>"
                )
        );

        assertThat(result.candidates()).isEmpty();
        assertThat(result.unsupportedBoardUrls()).singleElement()
                .isEqualTo(URI.create("https://jobs.eu.lever.co/acme"));
    }

    private BoardCandidateExtraction extract(String url, String fixture)
            throws IOException {
        String html = new ClassPathResource(fixture)
                .getContentAsString(StandardCharsets.UTF_8);
        return extractor.extract(new FetchedCareersPage(
                URI.create(url),
                html
        ));
    }
}
