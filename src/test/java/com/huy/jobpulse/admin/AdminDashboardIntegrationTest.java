package com.huy.jobpulse.admin;

import com.huy.jobpulse.admin.application.AdminEventService;
import com.huy.jobpulse.admin.infrastructure.AdminAuditRepository;
import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.application.IngestionTargetService;
import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = {
        "jobpulse.admin.enabled=true",
        "jobpulse.ingestion.initial-delay-ms=3600000",
        "jobpulse.events.publish-initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminDashboardIntegrationTest {
    private static final Instant NOW = Instant.now().minusSeconds(60)
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired IngestionTargetService targetService;
    @Autowired IngestionTargetRepository targets;
    @Autowired IngestionRequestRepository requests;
    @Autowired IngestionRunRepository runs;
    @Autowired IngestionDeadLetterRepository deadLetters;
    @Autowired JobPostingRepository postings;
    @Autowired JobEventRepository events;
    @Autowired AdminAuditRepository audits;
    @Autowired AdminEventService eventService;

    @BeforeEach
    void cleanDatabase() {
        audits.deleteAll(); deadLetters.deleteAll(); requests.deleteAll();
        events.deleteAll(); postings.deleteAll(); runs.deleteAll(); targets.deleteAll();
    }

    @Test
    void overviewUsesAccurateCountsAndLatestSuccessfulCompletion() throws Exception {
        var board = board("stripe", "Stripe");
        board("lever", "Lever");
        targetService.setEnabled(board.getId(), false);
        postings.save(posting("job-1", JobStatus.ACTIVE));
        JobPosting closed = postings.save(posting("job-2", JobStatus.ACTIVE));
        closed.closeManually(NOW);
        postings.save(closed);
        IngestionRun success = IngestionRun.start(JobSource.GREENHOUSE, "lever", NOW);
        success.succeed(2, 1, 0, 1, 0, NOW.plusSeconds(30));
        runs.save(success);
        IngestionRun failed = IngestionRun.start(JobSource.GREENHOUSE, "lever", NOW.plusSeconds(40));
        failed.fail("provider unavailable", NOW.plusSeconds(50));
        runs.save(failed);

        mvc.perform(get("/api/v1/admin/overview").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBoards").value(2))
                .andExpect(jsonPath("$.activeBoards").value(1))
                .andExpect(jsonPath("$.totalPostings").value(2))
                .andExpect(jsonPath("$.openPostings").value(1))
                .andExpect(jsonPath("$.failedRunsLast24Hours").value(1))
                .andExpect(jsonPath("$.lastSuccessfulRunAt")
                        .value(NOW.plusSeconds(30).toString()));
    }

    @Test
    void boardCanBeDisabledAndManualIngestionCreatesOneRequest() throws Exception {
        var board = board("stripe", "Stripe");
        mvc.perform(patch("/api/v1/admin/boards/{id}/enabled", board.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(targets.findById(board.getId()).orElseThrow().isEnabled()).isFalse();

        targetService.setEnabled(board.getId(), true);
        mvc.perform(post("/api/v1/admin/boards/{id}/ingestion", board.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ingestionTargetId").value(board.getId().toString()));
        assertThat(requests.count()).isOne();
    }

    @Test
    void retryCreatesNewRequestReferencingFailedRunWithoutChangingIt() throws Exception {
        var board = board("stripe", "Stripe");
        IngestionRun failed = IngestionRun.start(JobSource.GREENHOUSE, "stripe", NOW);
        failed.fail("provider unavailable", NOW.plusSeconds(5));
        runs.save(failed);

        mvc.perform(post("/api/v1/admin/ingestion-runs/{id}/retry", failed.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryOfRunId").value(failed.getId().toString()));

        assertThat(requests.findAll()).singleElement().satisfies(request ->
                assertThat(request.getRetryOfRunId()).isEqualTo(failed.getId()));
        assertThat(runs.findById(failed.getId()).orElseThrow().getFailureMessage())
                .isEqualTo("provider unavailable");
        assertThat(targets.findById(board.getId())).isPresent();
    }

    @Test
    void publishedEventsCannotBeMarkedForRetry() {
        JobPosting posting = postings.save(posting("event-job", JobStatus.ACTIVE));
        JobEvent event = JobEvent.capture(posting, JobEventType.CREATED, NOW);
        event.markPublished(NOW.plusSeconds(1));
        events.save(event);

        assertThatThrownBy(() -> eventService.requestRetry(event.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Published events");
    }

    @Test
    void unpublishedEventRetryOnlyMarksItEligibleForTheOutboxPublisher() {
        JobPosting posting = postings.save(posting("retry-event-job", JobStatus.ACTIVE));
        JobEvent event = events.save(JobEvent.capture(posting, JobEventType.CREATED, NOW));

        JobEvent retried = eventService.requestRetry(event.getId());

        assertThat(retried.getRetryRequestedAt()).isNotNull();
        assertThat(retried.getPublishedAt()).isNull();
        assertThat(retried.getPublishAttempts()).isZero();
    }

    @Test
    void boardsEndpointHandlesFiftyBoardFixture() throws Exception {
        for (int index = 0; index < 50; index++) {
            board("board-" + index, "Company " + index);
        }

        mvc.perform(get("/api/v1/admin/boards").param("size", "100")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(50))
                .andExpect(jsonPath("$.content.length()").value(50));
    }

    @Test
    void adminEndpointsRejectNonAdministrators() throws Exception {
        mvc.perform(get("/api/v1/admin/overview").with(request -> {
                    request.setRemoteAddr("203.0.113.10");
                    return request;
                }).with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private com.huy.jobpulse.ingestion.domain.IngestionTarget board(String account, String company) {
        return targetService.create(new CreateIngestionTargetRequest(JobSource.GREENHOUSE,
                account, company, "https://example.com/" + account, 60));
    }

    private static JobPosting posting(String sourceId, JobStatus ignored) {
        return JobPosting.create(JobSource.GREENHOUSE, "lever", sourceId,
                "Example", "Engineer", "Remote", "Build systems", "Full-time",
                RemotePolicy.REMOTE, "https://example.com/jobs/" + sourceId, NOW, NOW);
    }
}
