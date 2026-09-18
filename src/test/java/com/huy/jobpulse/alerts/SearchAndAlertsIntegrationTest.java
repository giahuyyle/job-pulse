package com.huy.jobpulse.alerts;

import com.huy.jobpulse.alerts.api.CreateSavedSearchRequest;
import com.huy.jobpulse.alerts.api.UpdateSavedSearchRequest;
import com.huy.jobpulse.alerts.application.AlertEventHandler;
import com.huy.jobpulse.alerts.application.AlertService;
import com.huy.jobpulse.alerts.application.SavedSearchService;
import com.huy.jobpulse.analytics.AnalyticsEventHandler;
import com.huy.jobpulse.alerts.domain.SavedSearch;
import com.huy.jobpulse.alerts.infrastructure.JobAlertRepository;
import com.huy.jobpulse.alerts.infrastructure.SavedSearchRepository;
import com.huy.jobpulse.ingestion.application.ExternalJob;
import com.huy.jobpulse.ingestion.application.IngestionWriter;
import com.huy.jobpulse.ingestion.application.RunRecorder;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.jobs.application.JobSearchCriteria;
import com.huy.jobpulse.jobs.application.JobSearchFilters;
import com.huy.jobpulse.jobs.application.JobSearchSort;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import com.huy.jobpulse.jobs.infrastructure.JobSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(SearchAndAlertsIntegrationTest.MutableClockConfiguration.class)
class SearchAndAlertsIntegrationTest {

    private static final Instant START = Instant.parse("2026-09-01T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired JobSearchRepository searchRepository;
    @Autowired JobPostingRepository jobRepository;
    @Autowired JobEventRepository eventRepository;
    @Autowired SavedSearchRepository savedSearchRepository;
    @Autowired JobAlertRepository alertRepository;
    @Autowired IngestionRunRepository runRepository;
    @Autowired SavedSearchService savedSearchService;
    @Autowired AlertEventHandler alertHandler;
    @Autowired AnalyticsEventHandler analyticsHandler;
    @Autowired AlertService alertService;
    @Autowired IngestionWriter ingestionWriter;
    @Autowired RunRecorder runRecorder;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        alertRepository.deleteAll();
        jdbc.update("DELETE FROM job_event_consumptions");
        jdbc.update("DELETE FROM daily_job_stats");
        eventRepository.deleteAll();
        savedSearchRepository.deleteAll();
        jobRepository.deleteAll();
        runRepository.deleteAll();
        clock.reset();
    }

    @Test
    void fullTextSearchFiltersSortsAndExcludesInactiveJobs() {
        JobPosting descriptionMatch = saveJob(
                "description-match",
                "Platform Engineer",
                "Build backend software systems",
                RemotePolicy.HYBRID,
                "Taipei"
        );
        clock.advance(Duration.ofMinutes(1));
        JobPosting titleMatch = saveJob(
                "title-match",
                "Backend Software Engineer",
                "Build reliable APIs",
                RemotePolicy.REMOTE,
                "Remote - US"
        );
        clock.advance(Duration.ofMinutes(1));
        JobPosting inactive = saveJob(
                "inactive",
                "Backend Engineer",
                "Closed role",
                RemotePolicy.REMOTE,
                "Remote"
        );
        inactive.recordMissing(clock.instant());
        inactive.recordMissing(clock.instant());
        jobRepository.save(inactive);

        var relevance = search("software engineer", null, null, null,
                JobSearchSort.RELEVANCE);
        assertThat(relevance.getContent())
                .extracting(hit -> hit.id())
                .containsExactly(titleMatch.getId(), descriptionMatch.getId());

        var phrase = search("\"backend software engineer\"", null, null, null,
                JobSearchSort.RELEVANCE);
        assertThat(phrase.getContent())
                .extracting(hit -> hit.id())
                .containsExactly(titleMatch.getId());

        var filtered = search(
                null,
                JobSource.GREENHOUSE,
                RemotePolicy.REMOTE,
                "US",
                JobSearchSort.NEWEST
        );
        assertThat(filtered.getContent())
                .extracting(hit -> hit.id())
                .containsExactly(titleMatch.getId());

        var recent = search(null, null, null, null, JobSearchSort.NEWEST);
        assertThat(recent.getContent())
                .extracting(hit -> hit.id())
                .containsExactly(titleMatch.getId(), descriptionMatch.getId())
                .doesNotContain(inactive.getId());
    }

    @Test
    void alertsOnlyForFutureNewMatchesAndRemainUnique() {
        saveJob(
                "historical",
                "Backend Engineer",
                "Existing role",
                RemotePolicy.REMOTE,
                "Remote"
        );
        clock.advance(Duration.ofMinutes(1));
        SavedSearch search = savedSearchService.create(
                new CreateSavedSearchRequest(
                        "Remote backend",
                        "backend engineer",
                        "Example Company",
                        JobSource.GREENHOUSE,
                        RemotePolicy.REMOTE,
                        "Remote"
                )
        );
        assertThat(alertRepository.count()).isZero();
        clock.advance(Duration.ofMinutes(1));

        List<ExternalJob> jobs = List.of(
                external("matching", "Backend Engineer", RemotePolicy.REMOTE),
                external("hybrid", "Backend Engineer", RemotePolicy.HYBRID)
        );
        ingest("alerts-board", jobs);

        assertThat(eventRepository.count()).isEqualTo(2);
        eventRepository.findAll().forEach(this::consume);
        assertThat(alertRepository.findAll()).singleElement().satisfies(alert -> {
            assertThat(alert.getSavedSearchId()).isEqualTo(search.getId());
            assertThat(alert.getReadAt()).isNull();
        });

        eventRepository.findAll().forEach(this::consume);
        ingest("alerts-board", jobs);
        eventRepository.findAll().forEach(this::consume);
        assertThat(alertRepository.count()).isOne();
        assertThat(dailyCount("CREATED")).isEqualTo(2);

        List<ExternalJob> changedJobs = List.of(
                external(
                        "matching",
                        "Principal Backend Engineer",
                        RemotePolicy.REMOTE
                ),
                external("hybrid", "Backend Engineer", RemotePolicy.HYBRID)
        );
        ingest("alerts-board", changedJobs);
        eventRepository.findAll().forEach(this::consume);
        assertThat(eventRepository.countByEventType(JobEventType.UPDATED))
                .isOne();
        assertThat(alertRepository.count()).isOne();
        assertThat(dailyCount("CREATED")).isEqualTo(2);

        var alert = alertService.findAll(true).getFirst();
        alertService.markRead(alert.alert().getId());
        assertThat(alertService.findAll(true)).isEmpty();

        UpdateSavedSearchRequest disable = new UpdateSavedSearchRequest();
        disable.setEnabled(false);
        savedSearchService.update(search.getId(), disable);
        clock.advance(Duration.ofMinutes(1));
        ingest("alerts-board", List.of(
                external(
                        "matching",
                        "Principal Backend Engineer",
                        RemotePolicy.REMOTE
                ),
                external("hybrid", "Backend Engineer", RemotePolicy.HYBRID),
                external("future", "Backend Engineer", RemotePolicy.REMOTE)
        ));
        eventRepository.findAll().forEach(this::consume);
        assertThat(alertRepository.count()).isOne();
        assertThat(dailyCount("CREATED")).isEqualTo(3);

        ingest("alerts-board", changedJobs);
        ingest("alerts-board", changedJobs);
        eventRepository.findAll().forEach(this::consume);
        assertThat(eventRepository.countByEventType(JobEventType.CLOSED))
                .isOne();
        assertThat(dailyCount("CLOSED")).isOne();
    }

    private void consume(JobEvent event) {
        var envelope = com.huy.jobpulse.events.JobEventEnvelope.from(event);
        alertHandler.process(envelope);
        analyticsHandler.process(envelope);
    }

    private long dailyCount(String eventType) {
        Long count = jdbc.queryForObject(
                "SELECT coalesce(sum(count), 0) FROM daily_job_stats "
                        + "WHERE event_type = ?",
                Long.class,
                eventType
        );
        return count == null ? 0 : count;
    }

    private org.springframework.data.domain.Page<com.huy.jobpulse.jobs.application.JobSearchHit>
    search(
            String query,
            JobSource source,
            RemotePolicy remotePolicy,
            String location,
            JobSearchSort sort
    ) {
        return searchRepository.search(new JobSearchCriteria(
                new JobSearchFilters(
                        query,
                        null,
                        source,
                        remotePolicy,
                        location
                ),
                sort,
                0,
                20
        ));
    }

    private JobPosting saveJob(
            String sourceJobId,
            String title,
            String description,
            RemotePolicy remotePolicy,
            String location
    ) {
        return jobRepository.save(JobPosting.create(
                JobSource.GREENHOUSE,
                "search-board",
                sourceJobId,
                "Example Company",
                title,
                location,
                description,
                "Full-time",
                remotePolicy,
                "https://example.com/jobs/" + sourceJobId,
                clock.instant(),
                clock.instant()
        ));
    }

    private ExternalJob external(
            String sourceJobId,
            String title,
            RemotePolicy remotePolicy
    ) {
        return new ExternalJob(
                sourceJobId,
                "Example Company",
                title,
                "Remote",
                "Build backend services",
                "Full-time",
                remotePolicy,
                "https://example.com/jobs/" + sourceJobId,
                clock.instant()
        );
    }

    private void ingest(String account, List<ExternalJob> jobs) {
        ingestionWriter.apply(
                runRecorder.start(JobSource.GREENHOUSE, account),
                JobSource.GREENHOUSE,
                account,
                jobs
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean @Primary
        MutableClock mutableClock() { return new MutableClock(START); }
    }

    static class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        void reset() { instant = START; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
