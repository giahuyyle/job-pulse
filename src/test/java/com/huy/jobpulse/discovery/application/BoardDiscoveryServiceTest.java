package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.discovery.domain.CompanySeed;
import com.huy.jobpulse.discovery.domain.CompanySeedStatus;
import com.huy.jobpulse.discovery.infrastructure.CompanySeedRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jobpulse.ingestion.initial-delay-ms=3600000",
        "jobpulse.discovery.initial-delay-ms=3600000"
})
@Testcontainers
@Import(BoardDiscoveryServiceTest.FakeDiscoveryConfiguration.class)
class BoardDiscoveryServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    BoardDiscoveryService discoveryService;

    @Autowired
    CompanySeedService seedService;

    @Autowired
    CompanySeedRepository seedRepository;

    @Autowired
    IngestionTargetRepository targetRepository;

    @Autowired
    FakeCareersPageFetcher pageFetcher;

    @Autowired
    @Qualifier("fakeGreenhouseVerifier")
    FakeBoardVerifier greenhouseVerifier;

    @Autowired
    @Qualifier("fakeLeverVerifier")
    FakeBoardVerifier leverVerifier;

    @Autowired
    @Qualifier("fakeAshbyVerifier")
    FakeBoardVerifier ashbyVerifier;

    @BeforeEach
    void reset() {
        seedRepository.deleteAll();
        targetRepository.deleteAll();
        pageFetcher.reset();
        greenhouseVerifier.reset();
        leverVerifier.reset();
        ashbyVerifier.reset();
    }

    @Test
    void severalLinksForOneGreenhouseBoardCreateOneTarget()
            throws IOException {
        CompanySeed seed = seed("Acme", "https://acme.example/careers");
        pageFetcher.page(
                seed.getCareersUrl(),
                seed.getCareersUrl(),
                fixture("fixtures/discovery-greenhouse.html")
        );
        greenhouseVerifier.valid("acme");

        DiscoveryRunResult result = discoveryService.runAll();

        assertThat(result.added()).isEqualTo(1);
        assertThat(targetRepository.findAll()).singleElement()
                .satisfies(target -> {
                    assertThat(target.getSource())
                            .isEqualTo(JobSource.GREENHOUSE);
                    assertThat(target.getSourceAccount()).isEqualTo("acme");
                });
    }

    @Test
    void redirectStraightToLeverBoardCreatesTarget() {
        CompanySeed seed = seed("Acme", "https://acme.example/jobs");
        pageFetcher.page(
                seed.getCareersUrl(),
                "https://jobs.lever.co/acme/engineering-role",
                "<html></html>"
        );
        leverVerifier.valid("acme");

        DiscoveryRunResult result = discoveryService.runAll();

        assertThat(result.added()).isEqualTo(1);
        assertThat(targetRepository.findAll()).singleElement()
                .satisfies(target -> {
                    assertThat(target.getSource()).isEqualTo(JobSource.LEVER);
                    assertThat(target.getSourceAccount()).isEqualTo("acme");
                });
    }

    @Test
    void ashbyLinkCreatesAshbyTarget() {
        CompanySeed seed = seed("OpenAI", "https://openai.example/careers");
        pageFetcher.page(
                seed.getCareersUrl(),
                seed.getCareersUrl(),
                "<a href='https://jobs.ashbyhq.com/openai'>Jobs</a>"
        );
        ashbyVerifier.valid("openai");

        DiscoveryRunResult result = discoveryService.runAll();

        assertThat(result.added()).isEqualTo(1);
        assertThat(targetRepository.findAll()).singleElement()
                .satisfies(target -> {
                    assertThat(target.getSource()).isEqualTo(JobSource.ASHBY);
                    assertThat(target.getSourceAccount()).isEqualTo("openai");
                });
    }

    @Test
    void twoVerifiedBoardsNeedReview() throws IOException {
        CompanySeed seed = seed("Acme", "https://acme.example/careers");
        pageFetcher.page(
                seed.getCareersUrl(),
                seed.getCareersUrl(),
                fixture("fixtures/discovery-multiple.html")
        );
        greenhouseVerifier.valid("acme");
        leverVerifier.valid("acme");

        DiscoveryRunResult result = discoveryService.runAll();

        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(result.outcomes().getFirst().error())
                .contains("Multiple verified boards");
        assertThat(targetRepository.count()).isZero();
    }

    @Test
    void javascriptOnlyPageNeedsReviewWithoutTarget() throws IOException {
        CompanySeed seed = seed("Acme", "https://acme.example/careers");
        pageFetcher.page(
                seed.getCareersUrl(),
                seed.getCareersUrl(),
                fixture("fixtures/discovery-js-only.html")
        );

        DiscoveryRunResult result = discoveryService.runAll();

        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(targetRepository.count()).isZero();
    }

    @Test
    void providerNotFoundAndTimeoutDoNotStopBatch() {
        CompanySeed missing = seed(
                "Missing",
                "https://missing.example/careers"
        );
        CompanySeed timeout = seed(
                "Timeout",
                "https://timeout.example/careers"
        );
        pageFetcher.page(
                missing.getCareersUrl(),
                missing.getCareersUrl(),
                "<a href='https://boards.greenhouse.io/missing'>Jobs</a>"
        );
        pageFetcher.page(
                timeout.getCareersUrl(),
                timeout.getCareersUrl(),
                "<a href='https://jobs.lever.co/slow'>Jobs</a>"
        );
        greenhouseVerifier.notFound("missing");
        leverVerifier.fail("slow", "provider timed out");

        DiscoveryRunResult result = discoveryService.runAll();

        assertThat(result.checked()).isEqualTo(2);
        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(result.errors()).isEqualTo(1);
        assertThat(targetRepository.count()).isZero();
    }

    @Test
    void secondRunReportsExistingWithoutDuplicateTarget() {
        CompanySeed seed = seed("Acme", "https://acme.example/careers");
        pageFetcher.page(
                seed.getCareersUrl(),
                seed.getCareersUrl(),
                "<a href='https://boards.greenhouse.io/acme'>Jobs</a>"
        );
        greenhouseVerifier.valid("acme");

        assertThat(discoveryService.runAll().added()).isEqualTo(1);
        DiscoveryRunResult second = discoveryService.runAll();

        assertThat(second.alreadyExists()).isEqualTo(1);
        assertThat(targetRepository.count()).isEqualTo(1);
    }

    @Test
    void importsCsvIdempotently() {
        String csv = "company_name,careers_url\n"
                + "Acme,https://acme.example/careers\n"
                + "Example,https://example.com/jobs\n";

        assertThat(seedService.importCsv(csv))
                .isEqualTo(new SeedImportResult(2, 0));
        assertThat(seedService.importCsv(csv))
                .isEqualTo(new SeedImportResult(0, 2));
        assertThat(seedRepository.count()).isEqualTo(2);
    }

    private CompanySeed seed(String company, String url) {
        return seedRepository.saveAndFlush(CompanySeed.create(company, url));
    }

    private static String fixture(String path) throws IOException {
        return new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeDiscoveryConfiguration {

        @Bean
        @Primary
        FakeCareersPageFetcher fakeCareersPageFetcher() {
            return new FakeCareersPageFetcher();
        }

        @Bean
        FakeBoardVerifier fakeGreenhouseVerifier() {
            return new FakeBoardVerifier(JobSource.GREENHOUSE);
        }

        @Bean
        FakeBoardVerifier fakeLeverVerifier() {
            return new FakeBoardVerifier(JobSource.LEVER);
        }

        @Bean
        FakeBoardVerifier fakeAshbyVerifier() {
            return new FakeBoardVerifier(JobSource.ASHBY);
        }

        @Bean
        @Primary
        BoardVerifierRegistry fakeBoardVerifierRegistry(
                @Qualifier("fakeGreenhouseVerifier")
                FakeBoardVerifier fakeGreenhouseVerifier,
                @Qualifier("fakeLeverVerifier")
                FakeBoardVerifier fakeLeverVerifier,
                @Qualifier("fakeAshbyVerifier")
                FakeBoardVerifier fakeAshbyVerifier
        ) {
            return new BoardVerifierRegistry(java.util.List.of(
                    fakeGreenhouseVerifier.asVerifier(),
                    fakeLeverVerifier.asVerifier(),
                    fakeAshbyVerifier.asVerifier()
            ));
        }
    }

    static class FakeCareersPageFetcher implements CareersPageFetcher {

        private final Map<URI, FetchedCareersPage> pages = new HashMap<>();

        void page(String requested, String finalUrl, String html) {
            pages.put(
                    URI.create(requested),
                    new FetchedCareersPage(URI.create(finalUrl), html)
            );
        }

        void reset() {
            pages.clear();
        }

        @Override
        public FetchedCareersPage fetch(URI uri) {
            FetchedCareersPage page = pages.get(uri);
            if (page == null) {
                throw new DiscoveryFetchException("No fake page for " + uri);
            }
            return page;
        }
    }

    static class FakeBoardVerifier {

        private final JobSource source;
        private final Map<String, BoardVerification> results = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();

        FakeBoardVerifier(JobSource source) {
            this.source = source;
        }

        private BoardVerification verify(String sourceAccount) {
            if (failures.containsKey(sourceAccount)) {
                throw new IllegalStateException(failures.get(sourceAccount));
            }
            return results.getOrDefault(
                    sourceAccount,
                    BoardVerification.notFound("not found")
            );
        }

        BoardVerifier asVerifier() {
            return new BoardVerifier() {
                @Override
                public JobSource source() {
                    return source;
                }

                @Override
                public BoardVerification verify(String sourceAccount) {
                    return FakeBoardVerifier.this.verify(sourceAccount);
                }
            };
        }

        void valid(String account) {
            results.put(account, BoardVerification.verified());
        }

        void notFound(String account) {
            results.put(account, BoardVerification.notFound("not found"));
        }

        void fail(String account, String message) {
            failures.put(account, message);
        }

        void reset() {
            results.clear();
            failures.clear();
        }
    }
}
