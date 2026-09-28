package com.huy.jobpulse.email;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.huy.jobpulse.email.EmailPreferences.ts;

@Service
public class EmailWebhooks {
    private final JdbcTemplate jdbc;
    private final EmailPreferences preferences;
    private final DigestQueue queue;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;
    public EmailWebhooks(JdbcTemplate jdbc, EmailPreferences preferences, DigestQueue queue,
            ObjectMapper mapper, Clock clock, MeterRegistry metrics) {
        this.jdbc = jdbc; this.preferences = preferences; this.queue = queue;
        this.mapper = mapper; this.clock = clock; this.metrics = metrics;
    }
    @Transactional
    public void receive(String eventId, byte[] body) {
        tools.jackson.databind.JsonNode event;
        try { event = mapper.readTree(body); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid email event JSON"); }
        String type = event.path("type").asText("");
        if (!Set.of("email.sent", "email.delivered", "email.bounced", "email.complained", "email.failed", "email.suppressed", "email.delivery_delayed").contains(type)) return;
        String provider = event.path("data").path("email_id").asText("");
        if (provider.isBlank() || provider.length() > 255) throw new IllegalArgumentException("Invalid email event");
        if (type.equals("email.bounced") && !"Permanent".equals(event.path("data").path("bounce").path("type").asText("Permanent")))
            type = "email.delivery_delayed";
        UUID digest = null;
        try { digest = UUID.fromString(event.path("data").path("tags").path("digest_id").asText("")); }
        catch (IllegalArgumentException ignored) { }
        if (digest != null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM email_digests WHERE id = ?)", Boolean.class, digest))) digest = null;
        Instant occurred;
        try { occurred = Instant.parse(event.path("created_at").asText()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid email event time"); }
        jdbc.update("""
                INSERT INTO email_webhook_events(event_id, provider_id, type, digest_id, occurred_at, received_at)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, eventId, provider, type, digest, ts(occurred), ts(clock.instant()));
        reconcileNext();
    }

    @Transactional
    public boolean reconcileNext() {
        var events = jdbc.query("""
                SELECT e.event_id, e.provider_id, e.type, d.id, d.owner_subject, d.recipient
                FROM email_webhook_events e JOIN email_digests d ON
                    (d.id = e.digest_id OR (e.digest_id IS NULL AND d.provider_id = e.provider_id))
                WHERE e.processed_at IS NULL AND d.first_attempt_at IS NOT NULL
                ORDER BY e.occurred_at, e.event_id LIMIT 1
                """, (rs, row) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6)));
        for (Event event : events) {
            var setting = preferences.lock(event.owner());
            jdbc.queryForObject("SELECT id FROM email_digests WHERE id = ? FOR UPDATE", UUID.class, event.digest());
            // Lock after settings, then recheck for a concurrent webhook processor.
            Boolean done = jdbc.queryForObject("SELECT processed_at IS NOT NULL FROM email_webhook_events WHERE event_id = ? FOR UPDATE", Boolean.class, event.id());
            if (Boolean.TRUE.equals(done)) continue;
            String state = jdbc.queryForObject("SELECT state FROM email_digests WHERE id = ?", String.class, event.digest());
            if (!"ACCEPTED".equals(state)) queue.accepted(event.digest(), event.owner(), event.provider(), clock.instant());
            String status = switch (event.type()) {
                case "email.delivered" -> "DELIVERED";
                case "email.bounced" -> "BOUNCED";
                case "email.complained" -> "COMPLAINED";
                case "email.failed" -> "FAILED";
                case "email.suppressed" -> "SUPPRESSED";
                case "email.delivery_delayed" -> "DELAYED";
                default -> "SENT";
            };
            // Terminal adverse outcomes cannot be overwritten by late sent/delivered events.
            jdbc.update("""
                    UPDATE email_digests SET delivery_status = CASE
                      WHEN delivery_status = 'COMPLAINED' THEN delivery_status
                      WHEN ? = 'COMPLAINED' THEN ?
                      WHEN delivery_status IN ('BOUNCED','FAILED','SUPPRESSED') THEN delivery_status
                      WHEN delivery_status = 'DELIVERED' AND ? IN ('SENT','DELAYED') THEN delivery_status
                      ELSE ? END WHERE id = ?
                    """, status, status, status, status, event.digest());
            if (status.equals("COMPLAINED") || ((status.equals("BOUNCED") || status.equals("SUPPRESSED"))
                    && setting.recipient().equals(event.recipient()))) {
                preferences.invalidate(event.owner());
                jdbc.update("""
                        UPDATE email_settings SET suppression = CASE WHEN suppression = 'COMPLAINT' THEN suppression ELSE ? END
                        WHERE owner_subject = ?
                        """, status.equals("COMPLAINED") ? "COMPLAINT" : status.equals("BOUNCED") ? "BOUNCE" : "PROVIDER", event.owner());
                jdbc.update("UPDATE email_candidates SET state = 'CANCELLED' WHERE owner_subject = ? AND state = 'WAITING'", event.owner());
            }
            jdbc.update("UPDATE email_webhook_events SET processed_at = ? WHERE event_id = ?", ts(clock.instant()), event.id());
            metrics.counter("jobpulse.email.delivery", "outcome", status.toLowerCase(Locale.ROOT)).increment();
        }
        return !events.isEmpty();
    }
    private record Event(String id, String provider, String type, UUID digest, String owner, String recipient) {}
}
