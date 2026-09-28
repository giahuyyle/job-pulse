package com.huy.jobpulse.email;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.huy.jobpulse.email.EmailPreferences.ts;

@Service
public class DigestQueue {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EmailPreferences preferences;
    private final DigestRenderer renderer;
    private final EmailProperties properties;
    private final MeterRegistry metrics;
    public DigestQueue(JdbcTemplate jdbc, Clock clock, EmailPreferences preferences,
            DigestRenderer renderer, EmailProperties properties, MeterRegistry metrics) {
        this.jdbc = jdbc; this.clock = clock; this.preferences = preferences;
        this.renderer = renderer; this.properties = properties; this.metrics = metrics;
    }

    @Transactional
    public boolean prepareNext() {
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE email_candidates c SET state = 'COVERED' FROM job_alerts a, email_covered_jobs x
                WHERE c.alert_id = a.id AND x.job_id = a.job_posting_id AND x.owner_subject = c.owner_subject
                  AND c.state = 'WAITING'
                """);
        jdbc.update("""
                UPDATE email_candidates SET state = 'EXPIRED'
                WHERE state = 'WAITING' AND created_at < ?
                """, ts(now.minus(Duration.ofDays(7))));
        var owners = jdbc.query("""
                SELECT owner_subject FROM email_settings WHERE next_digest_at <= ?
                ORDER BY last_accepted_at NULLS FIRST, next_digest_at, owner_subject
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getString(1), ts(now));
        if (owners.isEmpty()) return false;
        String owner = owners.getFirst();
        var setting = preferences.lock(owner);
        // Catch up through the latest elapsed Eastern slot, without replaying missed days.
        LocalDate date = now.atZone(DigestSchedule.EASTERN).toLocalDate();
        Instant cutoff = DigestSchedule.at(date);
        if (cutoff.isAfter(now)) { date = date.minusDays(1); cutoff = DigestSchedule.at(date); }
        jdbc.update("UPDATE email_settings SET next_digest_at = ? WHERE owner_subject = ?",
                ts(DigestSchedule.next(now)), owner);
        if (setting.suppression() != null) return true;
        var candidates = jdbc.query("""
                SELECT c.alert_id, j.id AS job_id, j.title, j.company, j.location, j.remote_policy, j.apply_url, s.name
                FROM email_candidates c JOIN job_alerts a ON a.id = c.alert_id
                JOIN saved_searches s ON s.id = a.saved_search_id
                JOIN job_postings j ON j.id = a.job_posting_id
                WHERE c.owner_subject = ? AND c.state = 'WAITING' AND c.created_at < ?
                  AND c.created_at >= ? AND s.enabled AND s.email_enabled AND j.status = 'ACTIVE'
                  AND NOT EXISTS(SELECT 1 FROM email_covered_jobs x WHERE x.owner_subject = c.owner_subject AND x.job_id = j.id)
                  AND NOT EXISTS(SELECT 1 FROM email_candidates held JOIN job_alerts ha ON ha.id = held.alert_id
                      WHERE held.owner_subject = c.owner_subject AND held.state = 'RESERVED' AND ha.job_posting_id = j.id)
                ORDER BY j.first_seen_at DESC, j.id, s.name
                FOR UPDATE OF c
                """, (rs, row) -> new Candidate(rs.getObject("alert_id", UUID.class),
                new DigestRenderer.Job(rs.getObject("job_id", UUID.class), rs.getString("title"), rs.getString("company"),
                        rs.getString("location"), rs.getString("remote_policy"), rs.getString("apply_url"),
                        new TreeSet<>(Set.of(rs.getString("name"))))), owner, ts(cutoff), ts(now.minus(Duration.ofDays(7))));
        if (candidates.isEmpty()) return true;
        UUID id = UUID.randomUUID();
        var jobs = new LinkedHashMap<UUID, DigestRenderer.Job>();
        for (Candidate candidate : candidates) {
            var existing = jobs.putIfAbsent(candidate.job().id(), candidate.job());
            if (existing != null) existing.searchNames().addAll(candidate.job().searchNames());
        }
        int inserted = jdbc.update("""
                INSERT INTO email_digests(id, owner_subject, schedule_date, cutoff_at, recipient,
                    settings_version, next_attempt_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, id, owner, date, ts(cutoff), setting.recipient(), setting.version(), ts(now));
        if (inserted == 0) return true;
        String token = preferences.issueToken(owner, setting.recipient());
        String payload = renderer.render(new ArrayList<>(jobs.values()), setting.recipient(), token, id);
        jdbc.update("UPDATE email_digests SET payload = ? WHERE id = ?", payload, id);
        for (Candidate candidate : candidates) jdbc.update("""
                UPDATE email_candidates SET state = 'RESERVED', digest_id = ? WHERE alert_id = ?
                """, id, candidate.alertId());
        metrics.counter("jobpulse.email.digests", "outcome", "prepared").increment();
        return true;
    }

    @Transactional
    public Optional<Claim> claimNext() {
        Instant now = clock.instant();
        // Acquisition order is settings -> digest -> budget in all preference and sending paths.
        var owners = jdbc.query("""
                SELECT s.owner_subject FROM email_settings s WHERE EXISTS(
                  SELECT 1 FROM email_digests d WHERE d.owner_subject = s.owner_subject
                    AND ((d.state = 'PENDING' AND d.next_attempt_at <= ?)
                      OR (d.state = 'SENDING' AND d.lease_expires_at <= ?)))
                ORDER BY s.last_accepted_at NULLS FIRST, s.owner_subject
                LIMIT 1 FOR UPDATE OF s SKIP LOCKED
                """, (rs, row) -> rs.getString(1), ts(now), ts(now));
        if (owners.isEmpty()) return Optional.empty();
        String owner = owners.getFirst();
        var settings = preferences.lock(owner);
        var rows = jdbc.query("""
                SELECT * FROM email_digests WHERE owner_subject = ?
                  AND ((state = 'PENDING' AND next_attempt_at <= ?) OR (state = 'SENDING' AND lease_expires_at <= ?))
                ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Pending(rs.getObject("id", UUID.class), rs.getString("recipient"),
                rs.getString("payload"), rs.getLong("settings_version"), rs.getInt("attempts"),
                rs.getTimestamp("first_attempt_at") == null ? null : rs.getTimestamp("first_attempt_at").toInstant()),
                owner, ts(now), ts(now));
        if (rows.isEmpty()) return Optional.empty();
        Pending pending = rows.getFirst();
        if (pending.firstAttempt() != null && !now.isBefore(pending.firstAttempt().plus(Duration.ofHours(23)))) {
            terminal(pending.id(), "UNCERTAIN", "retry_window_expired", false); return Optional.empty();
        }
        boolean valid = settings.version() == pending.version() && settings.suppression() == null
                && settings.recipient().equals(pending.recipient());
        Long invalidMembers = jdbc.queryForObject("""
                SELECT count(*) FROM email_candidates c JOIN job_alerts a ON a.id = c.alert_id
                  JOIN saved_searches s ON s.id = a.saved_search_id JOIN job_postings j ON j.id = a.job_posting_id
                WHERE c.digest_id = ? AND (NOT s.enabled OR NOT s.email_enabled OR j.status <> 'ACTIVE' OR c.created_at < ?)
                """, Long.class, pending.id(), ts(now.minus(Duration.ofDays(7))));
        if (!valid || (pending.firstAttempt() == null && invalidMembers != null && invalidMembers > 0)) {
            terminal(pending.id(), pending.firstAttempt() == null ? "CANCELLED" : "UNCERTAIN", "ineligible", pending.firstAttempt() == null);
            return Optional.empty();
        }
        if (!properties.allowed(pending.recipient())) {
            terminal(pending.id(), pending.firstAttempt() == null ? "DEFERRED" : "UNCERTAIN", "rollout_allowlist", pending.firstAttempt() == null); return Optional.empty();
        }
        if (!reserve(pending.id(), now)) {
            terminal(pending.id(), pending.firstAttempt() == null ? "DEFERRED" : "UNCERTAIN", "budget_exhausted", pending.firstAttempt() == null); return Optional.empty();
        }
        UUID lease = UUID.randomUUID();
        jdbc.update("""
                UPDATE email_digests SET state = 'SENDING', lease_owner = ?, lease_expires_at = ?,
                    error_code = CASE WHEN state = 'SENDING' THEN 'ambiguous' ELSE error_code END,
                    first_attempt_at = coalesce(first_attempt_at, ?), attempts = attempts + 1 WHERE id = ?
                """, lease, ts(now.plusSeconds(60)), ts(now), pending.id());
        return Optional.of(new Claim(pending.id(), lease, pending.payload()));
    }

    private boolean reserve(UUID digest, Instant now) {
        var blocked = jdbc.queryForObject("SELECT blocked_until FROM email_budget WHERE id = 1 FOR UPDATE", java.sql.Timestamp.class);
        LocalDate budgetDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        boolean existing = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM email_budget_reservations WHERE digest_id = ? AND budget_date = ? AND NOT released)", Boolean.class, digest, budgetDate));
        if (blocked != null && blocked.toInstant().isAfter(now)) return false;
        if (existing) return true;
        Instant day = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant month = now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Long daily = jdbc.queryForObject("SELECT count(DISTINCT digest_id) FROM email_budget_reservations WHERE NOT released AND reserved_at >= ?", Long.class, ts(day));
        Long monthly = jdbc.queryForObject("SELECT count(DISTINCT digest_id) FROM email_budget_reservations WHERE NOT released AND reserved_at >= ?", Long.class, ts(month));
        if (daily >= properties.dailyLimit() || monthly >= properties.monthlyLimit()) return false;
        jdbc.update("INSERT INTO email_budget_reservations(digest_id, budget_date, reserved_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", digest, budgetDate, ts(now));
        return true;
    }

    @Transactional
    public void finish(Claim claim, EmailProvider.Result result) {
        String owner = jdbc.queryForObject("SELECT owner_subject FROM email_digests WHERE id = ?", String.class, claim.id());
        preferences.lock(owner);
        var rows = jdbc.query("SELECT * FROM email_digests WHERE id = ? FOR UPDATE", (rs, row) ->
                new Completion(rs.getString("state"), rs.getObject("lease_owner", UUID.class), rs.getInt("attempts"),
                        rs.getTimestamp("first_attempt_at").toInstant(), rs.getString("recipient")), claim.id());
        Completion current = rows.getFirst();
        if (!"SENDING".equals(current.state()) || !claim.lease().equals(current.lease())) return;
        Instant now = clock.instant();
        if (result.outcome() == EmailProvider.Outcome.ACCEPTED) {
            accepted(claim.id(), owner, result.providerId(), now);
        } else if (result.outcome() == EmailProvider.Outcome.QUOTA_DAILY || result.outcome() == EmailProvider.Outcome.QUOTA_MONTHLY) {
            Instant reset = result.outcome() == EmailProvider.Outcome.QUOTA_DAILY
                    ? now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    : now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            jdbc.update("UPDATE email_budget SET blocked_until = greatest(coalesce(blocked_until, ?), ?) WHERE id = 1", ts(reset), ts(reset));
            // A previous unknown attempt could already have been accepted; don't release it blindly.
            boolean ambiguous = Boolean.TRUE.equals(jdbc.queryForObject("SELECT error_code = 'ambiguous' FROM email_digests WHERE id = ?", Boolean.class, claim.id()));
            terminal(claim.id(), ambiguous ? "UNCERTAIN" : "DEFERRED", result.code(), !ambiguous);
            if (!ambiguous) jdbc.update("UPDATE email_budget_reservations SET released = TRUE WHERE digest_id = ?", claim.id());
        } else {
            boolean unknownBefore = Boolean.TRUE.equals(jdbc.queryForObject("SELECT error_code = 'ambiguous' FROM email_digests WHERE id = ?", Boolean.class, claim.id()));
            boolean ambiguous = unknownBefore || result.outcome() == EmailProvider.Outcome.UNKNOWN;
            long[] delays = {60, 300, 900, 3600};
            Duration delay = Duration.ofSeconds(delays[Math.min(current.attempts() - 1, delays.length - 1)]);
            if (result.retryAfter().compareTo(delay) > 0) delay = result.retryAfter();
            Instant next = now.plus(delay);
            boolean stop = current.attempts() >= 5 || !next.isBefore(current.firstAttempt().plus(Duration.ofHours(23)))
                    || result.outcome() == EmailProvider.Outcome.PERMANENT;
            if (stop) terminal(claim.id(), ambiguous ? "UNCERTAIN" : "FAILED", result.code(), false);
            else jdbc.update("""
                    UPDATE email_digests SET state = 'PENDING', next_attempt_at = ?, lease_owner = NULL,
                        lease_expires_at = NULL, error_code = ? WHERE id = ?
                    """, ts(next), ambiguous ? "ambiguous" : result.code(), claim.id());
        }
        metrics.counter("jobpulse.email.attempts", "outcome", result.outcome().name().toLowerCase(Locale.ROOT)).increment();
    }

    void accepted(UUID id, String owner, String providerId, Instant now) {
        jdbc.update("""
                UPDATE email_digests SET state = 'ACCEPTED', provider_id = ?, accepted_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, error_code = NULL WHERE id = ?
                """, providerId, ts(now), id);
        jdbc.update("UPDATE email_candidates SET state = 'COVERED' WHERE digest_id = ?", id);
        jdbc.update("""
                INSERT INTO email_covered_jobs(owner_subject, job_id, digest_id)
                SELECT DISTINCT c.owner_subject, a.job_posting_id, c.digest_id FROM email_candidates c
                JOIN job_alerts a ON a.id = c.alert_id WHERE c.digest_id = ? ON CONFLICT DO NOTHING
                """, id);
        jdbc.update("UPDATE email_settings SET last_accepted_at = ? WHERE owner_subject = ?", ts(now), owner);
        metrics.counter("jobpulse.email.digests", "outcome", "accepted").increment();
    }
    private void terminal(UUID id, String state, String code, boolean release) {
        jdbc.update("UPDATE email_digests SET state = ?, error_code = ?, lease_owner = NULL, lease_expires_at = NULL WHERE id = ?", state, code, id);
        if (release) jdbc.update("UPDATE email_candidates SET state = 'WAITING', digest_id = NULL WHERE digest_id = ? AND state = 'RESERVED'", id);
        metrics.counter("jobpulse.email.digests", "outcome", state.toLowerCase(Locale.ROOT)).increment();
    }
    public record Claim(UUID id, UUID lease, String payload) { public String key() { return "jobpulse-digest/" + id; } }
    private record Candidate(UUID alertId, DigestRenderer.Job job) {}
    private record Pending(UUID id, String recipient, String payload, long version, int attempts, Instant firstAttempt) {}
    private record Completion(String state, UUID lease, int attempts, Instant firstAttempt, String recipient) {}
}
