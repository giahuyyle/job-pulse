package com.huy.jobpulse.alerts;

import com.huy.jobpulse.email.*;
import com.huy.jobpulse.alerts.api.*;
import com.huy.jobpulse.jobs.domain.*;
import com.huy.jobpulse.ingestion.application.ExternalJob;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"jobpulse.email.enabled=true", "jobpulse.email.api-key=test-key",
        "jobpulse.email.from=JobPulse <alerts@example.com>", "jobpulse.email.app-url=https://jobpulse.example",
        "jobpulse.email.daily-limit=2", "jobpulse.email.monthly-limit=3"})
@AutoConfigureMockMvc
class EmailDigestIntegrationTest extends SearchAndAlertsIntegrationTest {
    // Generate test-only signing material at runtime; never commit credential-shaped fixtures.
    private static final byte[] WEBHOOK_KEY = new byte[32];
    static { new java.security.SecureRandom().nextBytes(WEBHOOK_KEY); }

    @DynamicPropertySource
    static void webhookProperties(DynamicPropertyRegistry properties) {
        properties.add("jobpulse.email.webhook-secret",
                () -> "whsec_" + Base64.getEncoder().encodeToString(WEBHOOK_KEY));
    }

    @Autowired DigestQueue queue;
    @Autowired EmailPreferences preferences;
    @Autowired EmailWebhooks webhooks;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EmailMetrics emailMetrics;
    @Autowired io.micrometer.core.instrument.MeterRegistry registry;

    @BeforeEach void clearEmail() {
        jdbc.execute("TRUNCATE email_settings CASCADE");
        jdbc.update("UPDATE email_budget SET blocked_until = NULL");
    }
    private UUID subscribe(String owner, String name) {
        return savedSearchService.create(owner, owner + "@example.com", new CreateSavedSearchRequest(
                name, "engineer", null, null, null, null, true)).getId();
    }
    private void ingestEmail(String id) {
        clock.advance(Duration.ofSeconds(1));
        ingestionWriter.apply(runRecorder.start(JobSource.GREENHOUSE, "email-board"), JobSource.GREENHOUSE,
                "email-board", List.of(new ExternalJob(id, "Example", "Backend Engineer", "Remote", "Build APIs", "Full-time",
                        RemotePolicy.REMOTE, "https://example.com/" + id, clock.instant())));
        eventRepository.findAll().forEach(event -> alertHandler.process(com.huy.jobpulse.events.JobEventEnvelope.from(event)));
    }
    private void due() { clock.advance(Duration.between(clock.instant(), Instant.parse("2026-09-01T13:00:00Z"))); }
    private String state() { return jdbc.queryForObject("SELECT state FROM email_digests", String.class); }
    private void prepareAll() { while (queue.prepareNext()) { } }

    @Test void combinesSearchesIgnoresReadAndDeduplicatesKafkaReplay() {
        subscribe("alice", "First"); subscribe("alice", "Second");
        ingestEmail("one");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_candidates", Long.class)).isEqualTo(2);
        alertRepository.findAll().forEach(alert -> alertService.markRead("alice", alert.getId()));
        assertThat(queue.prepareNext()).isFalse(); due(); prepareAll();
        var claim = queue.claimNext().orElseThrow();
        var json = mapper.readTree(claim.payload());
        assertThat(json.path("subject").asText()).contains("1 new jobs");
        assertThat(json.path("text").asText()).contains("First", "Second");
        var fake = new FakeEmailProvider(); queue.finish(claim, fake.send(claim.payload(), claim.key()));
        assertThat(state()).isEqualTo("ACCEPTED"); assertThat(queue.claimNext()).isEmpty();
        eventRepository.findAll().forEach(event -> alertHandler.process(com.huy.jobpulse.events.JobEventEnvelope.from(event)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_digests", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_covered_jobs", Long.class)).isOne();
    }
    @Test void optInDoesNotBackfillAndAfterCutoffWaitsUntilTomorrow() {
        var search = savedSearchService.create("alice", new CreateSavedSearchRequest("Inbox", "engineer", null, null, null, null));
        ingestEmail("old");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_candidates", Long.class)).isZero();
        due();
        var change = new UpdateSavedSearchRequest(); change.setEmailEnabled(true);
        savedSearchService.update("alice", "alice@example.com", search.getId(), change);
        assertThat(preferences.view("alice", "alice@example.com").nextDigestAt()).isEqualTo(Instant.parse("2026-09-02T13:00:00Z"));
        assertThat(queue.prepareNext()).isFalse();
    }
    @Test void delayedOldKafkaEventCannotBackfillAnExistingSearchOptIn() {
        var search = savedSearchService.create("alice", new CreateSavedSearchRequest("Inbox", "engineer", null, null, null, null));
        clock.advance(Duration.ofSeconds(1));
        ingestionWriter.apply(runRecorder.start(JobSource.GREENHOUSE, "late-board"), JobSource.GREENHOUSE,
                "late-board", List.of(new ExternalJob("late", "Example", "Backend Engineer", "Remote", "Build APIs", "Full-time",
                        RemotePolicy.REMOTE, "https://example.com/late", clock.instant())));
        clock.advance(Duration.ofMinutes(1));
        var change = new UpdateSavedSearchRequest(); change.setEmailEnabled(true);
        savedSearchService.update("alice", "alice@example.com", search.getId(), change);
        eventRepository.findAll().forEach(event -> alertHandler.process(com.huy.jobpulse.events.JobEventEnvelope.from(event)));
        assertThat(alertRepository.count()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_candidates", Long.class)).isZero();
    }
    @Test void retryAcrossUtcMidnightReservesTheNewDayWithoutDoubleCountingTheMonth() {
        subscribe("alice", "First"); ingestEmail("cross-midnight"); due(); prepareAll();
        var first = queue.claimNext().orElseThrow();
        queue.finish(first, new EmailProvider.Result(EmailProvider.Outcome.TRANSIENT, null, "rate_limit_exceeded", Duration.ofHours(13)));
        clock.advance(Duration.ofHours(13));
        var retry = queue.claimNext().orElseThrow();
        assertThat(retry.key()).isEqualTo(first.key());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_budget_reservations", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT digest_id) FROM email_budget_reservations", Long.class)).isOne();
        queue.finish(retry, EmailProvider.Result.accepted("cross-day-provider"));
    }
    @Test void crashedLeaseRetriesTheSameKeyAndRejectsStaleCompletion() {
        subscribe("alice", "First"); ingestEmail("lease"); due(); prepareAll();
        var old = queue.claimNext().orElseThrow(); clock.advance(Duration.ofSeconds(61));
        var reclaimed = queue.claimNext().orElseThrow();
        assertThat(reclaimed.key()).isEqualTo(old.key()); assertThat(reclaimed.lease()).isNotEqualTo(old.lease());
        queue.finish(old, EmailProvider.Result.accepted("stale-provider"));
        assertThat(state()).isEqualTo("SENDING");
        queue.finish(reclaimed, EmailProvider.Result.accepted("new-provider"));
        assertThat(state()).isEqualTo("ACCEPTED");
    }
    @Test void candidateAfterCutoffWaitsForTheNextDay() {
        subscribe("alice", "First"); ingestEmail("before"); due();
        ingestEmail("after"); prepareAll();
        var first = queue.claimNext().orElseThrow();
        assertThat(mapper.readTree(first.payload()).path("text").asText()).contains("https://example.com/before").doesNotContain("https://example.com/after");
        queue.finish(first, EmailProvider.Result.accepted("before-provider"));
        clock.advance(Duration.ofDays(1)); prepareAll();
        var second = queue.claimNext().orElseThrow();
        assertThat(mapper.readTree(second.payload()).path("text").asText()).contains("https://example.com/after");
    }
    @Test void retriesUseSamePayloadAndReservation() {
        subscribe("alice", "First"); ingestEmail("retry"); due(); prepareAll();
        var first = queue.claimNext().orElseThrow();
        queue.finish(first, new EmailProvider.Result(EmailProvider.Outcome.UNKNOWN, null, "network_error", Duration.ZERO));
        assertThat(queue.claimNext()).isEmpty(); clock.advance(Duration.ofMinutes(1));
        var retry = queue.claimNext().orElseThrow();
        assertThat(retry.payload()).isEqualTo(first.payload()); assertThat(retry.key()).isEqualTo(first.key());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_budget_reservations", Long.class)).isOne();
        queue.finish(retry, EmailProvider.Result.accepted("retry-provider")); assertThat(state()).isEqualTo("ACCEPTED");
    }
    @Test void uncertainSendIsNotRetriedPastSafetyWindow() {
        subscribe("alice", "First"); ingestEmail("unknown"); due(); prepareAll();
        queue.claimNext().orElseThrow(); // simulate crash after sending, before recording outcome
        clock.advance(Duration.ofHours(24));
        assertThat(queue.claimNext()).isEmpty(); assertThat(state()).isEqualTo("UNCERTAIN");
        assertThat(jdbc.queryForObject("SELECT state FROM email_candidates", String.class)).isEqualTo("RESERVED");
    }
    @Test void pauseAndRecipientChangeCancelPendingMessages() {
        UUID id = subscribe("alice", "First"); ingestEmail("cancel"); due(); prepareAll();
        var pause = new UpdateSavedSearchRequest(); pause.setEnabled(false);
        savedSearchService.update("alice", id, pause);
        assertThat(state()).isEqualTo("CANCELLED"); assertThat(queue.claimNext()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT state FROM email_candidates", String.class)).isEqualTo("CANCELLED");
        preferences.synchronizeRecipient("alice", "new@example.com");
        assertThat(preferences.view("alice", "new@example.com").recipient()).isEqualTo("new@example.com");
    }
    @Test void budgetsCarryMatchesToLaterDigestWithoutLosingInboxAlerts() {
        subscribe("alice", "First"); subscribe("bob", "Second"); subscribe("carol", "Third");
        ingestEmail("quota"); due(); prepareAll();
        for (int i = 0; i < 2; i++) {
            var claim = queue.claimNext().orElseThrow(); queue.finish(claim, EmailProvider.Result.accepted("quota-" + i));
        }
        assertThat(queue.claimNext()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_digests WHERE state = 'DEFERRED'", Long.class)).isOne();
        clock.advance(Duration.ofDays(1)); prepareAll();
        var later = queue.claimNext().orElseThrow(); queue.finish(later, EmailProvider.Result.accepted("quota-later"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_covered_jobs", Long.class)).isEqualTo(3);
        assertThat(alertRepository.count()).isEqualTo(3);
        subscribe("dave", "Fourth"); ingestEmail("month-quota"); clock.advance(Duration.ofDays(1)); prepareAll();
        assertThat(queue.claimNext()).isEmpty(); // monthly budget is 3
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_budget_reservations WHERE NOT released", Long.class)).isEqualTo(3);
    }
    @Test void providerQuotaBlocksOtherWorkersAndRetainsCandidates() {
        subscribe("alice", "First"); ingestEmail("provider-quota"); due(); prepareAll();
        var claim = queue.claimNext().orElseThrow();
        queue.finish(claim, new EmailProvider.Result(EmailProvider.Outcome.QUOTA_MONTHLY, null, "monthly_quota_exceeded", Duration.ZERO));
        assertThat(state()).isEqualTo("DEFERRED");
        assertThat(jdbc.queryForObject("SELECT state FROM email_candidates", String.class)).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("SELECT blocked_until FROM email_budget", java.sql.Timestamp.class).toInstant()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }
    @Test void restartCatchUpIncludesMissedSlotsAndExpiresSevenDayOldMatches() {
        subscribe("alice", "First"); ingestEmail("catch-up");
        clock.advance(Duration.ofDays(3)); prepareAll();
        assertThat(jdbc.queryForObject("SELECT schedule_date FROM email_digests", java.sql.Date.class).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(jdbc.queryForObject("SELECT next_digest_at FROM email_settings", java.sql.Timestamp.class).toInstant()).isEqualTo(Instant.parse("2026-09-04T13:00:00Z"));
        // Simulate an unsent digest deferred by quota, then allow retention to expire.
        var claim = queue.claimNext().orElseThrow();
        queue.finish(claim, new EmailProvider.Result(EmailProvider.Outcome.QUOTA_DAILY, null, "daily_quota_exceeded", Duration.ZERO));
        clock.advance(Duration.ofDays(5)); prepareAll();
        assertThat(jdbc.queryForObject("SELECT state FROM email_candidates", String.class)).isEqualTo("EXPIRED");
        assertThat(alertRepository.count()).isOne();
    }
    @Test void simultaneousWorkersClaimADigestOnlyOnce() throws Exception {
        subscribe("alice", "First"); ingestEmail("concurrent"); due(); prepareAll();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Optional<DigestQueue.Claim>> task = () -> { start.await(); return queue.claimNext(); };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            long claims = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).stream().filter(Optional::isPresent).count();
            assertThat(claims).isOne();
        }
    }
    @Test void signedEarlyWebhookReconcilesAcceptanceAndLateEventsCannotUndoComplaint() {
        subscribe("alice", "First"); ingestEmail("webhook"); due(); prepareAll();
        var claim = queue.claimNext().orElseThrow();
        webhooks.receive("event-delivered", event("email.delivered", claim.id()));
        assertThat(state()).isEqualTo("ACCEPTED");
        queue.finish(claim, EmailProvider.Result.accepted("provider-event"));
        webhooks.receive("event-complaint", event("email.complained", claim.id()));
        webhooks.receive("event-delivered", event("email.delivered", claim.id()));
        webhooks.receive("late-sent", event("email.sent", claim.id()));
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM email_digests", String.class)).isEqualTo("COMPLAINED");
        assertThat(preferences.view("alice", "alice@example.com").suppression()).isEqualTo("COMPLAINT");
        preferences.synchronizeRecipient("alice", "new@example.com");
        assertThat(preferences.view("alice", "new@example.com").suppression()).isEqualTo("COMPLAINT");
        var optIn = new UpdateSavedSearchRequest(); optIn.setEmailEnabled(true);
        savedSearchService.update("alice", "new@example.com", savedSearchRepository.findAll().getFirst().getId(), optIn);
        assertThat(preferences.view("alice", "new@example.com").suppression()).isNull();
    }
    @Test void webhookEndpointAuthenticatesRawBytesAndSuppressesHardBounces() throws Exception {
        subscribe("alice", "First"); ingestEmail("signature"); due(); prepareAll();
        var claim = queue.claimNext().orElseThrow(); byte[] body = event("email.bounced", claim.id());
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(WEBHOOK_KEY, "HmacSHA256"));
        mac.update(("signed-event." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        String signature = "v1," + Base64.getEncoder().encodeToString(mac.doFinal(body));
        mvc.perform(post("/api/v1/webhooks/resend").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("svix-id", "signed-event").header("svix-timestamp", timestamp).header("svix-signature", signature))
                .andExpect(status().isOk());
        assertThat(preferences.view("alice", "alice@example.com").suppression()).isEqualTo("BOUNCE");
        preferences.synchronizeRecipient("alice", "new@example.com");
        assertThat(preferences.view("alice", "new@example.com").suppression()).isNull();
        mvc.perform(post("/api/v1/webhooks/resend").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("svix-id", "signed-event").header("svix-timestamp", timestamp).header("svix-signature", signature))
                .andExpect(status().isBadRequest());
    }
    @Test void failedAndSuppressedProviderDeliveryAreVisibleToOperations() {
        subscribe("alice", "First"); ingestEmail("suppressed"); due(); prepareAll();
        var claim = queue.claimNext().orElseThrow();
        webhooks.receive("event-suppressed", event("email.suppressed", claim.id()));
        assertThat(preferences.view("alice", "alice@example.com").suppression()).isEqualTo("PROVIDER");
        emailMetrics.refresh();
        assertThat(registry.get("jobpulse.email.failed").gauge().value()).isEqualTo(1);
        assertThat(registry.get("jobpulse.email.suppressed").gauge().value()).isEqualTo(1);
    }
    private byte[] event(String type, UUID digest) {
        return mapper.writeValueAsBytes(Map.of("type", type, "created_at", clock.instant().toString(),
                "data", Map.of("email_id", "provider-event", "tags", Map.of("digest_id", digest.toString()))));
    }
    @Test void unsubscribeGetDoesNotMutateAndPostNeedsOnlyValidToken() throws Exception {
        subscribe("alice", "First"); ingestEmail("unsubscribe"); due(); prepareAll();
        String payload = jdbc.queryForObject("SELECT payload FROM email_digests", String.class);
        String text = mapper.readTree(payload).path("text").asText();
        String token = text.substring(text.lastIndexOf("token=") + 6).trim();
        mvc.perform(get("/email/unsubscribe").param("token", token)).andExpect(status().isOk());
        assertThat(savedSearchRepository.findAll().getFirst().isEmailEnabled()).isTrue();
        mvc.perform(post("/api/v1/email/unsubscribe").param("token", token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/email/unsubscribe").param("token", token)).andExpect(status().isOk());
        assertThat(savedSearchRepository.findAll().getFirst().isEmailEnabled()).isFalse();
        assertThat(state()).isEqualTo("CANCELLED"); assertThat(alertRepository.count()).isOne();
        mvc.perform(post("/api/v1/email/unsubscribe").param("token", "a".repeat(43))).andExpect(status().isNotFound());
    }
    @Test void emailApiRequiresOwnershipVerificationAndCsrf() throws Exception {
        UUID id = subscribe("alice", "First");
        mvc.perform(get("/api/v1/email-settings")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/saved-searches/{id}", id).with(oidcLogin().idToken(t -> t.claim("sub", "bob")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"emailEnabled\":false}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/saved-searches/{id}", id).with(oidcLogin().idToken(t -> t.claim("sub", "alice")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailEnabled\":false}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/saved-searches").with(oidcLogin().idToken(t -> t.claim("email_verified", false)))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Email\",\"emailEnabled\":true}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/webhooks/resend").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
