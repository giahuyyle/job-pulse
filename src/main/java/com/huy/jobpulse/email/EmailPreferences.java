package com.huy.jobpulse.email;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class EmailPreferences {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EmailProperties properties;
    public EmailPreferences(JdbcTemplate jdbc, Clock clock, EmailProperties properties) {
        this.jdbc = jdbc; this.clock = clock; this.properties = properties;
    }

    @Transactional
    public void optIn(String owner, String verifiedEmail) {
        requireEmail(verifiedEmail);
        jdbc.update("""
                INSERT INTO email_settings(owner_subject, recipient, next_digest_at)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, owner, verifiedEmail, ts(DigestSchedule.next(clock.instant())));
        synchronizeRecipient(owner, verifiedEmail);
        var setting = lock(owner);
        boolean previouslySubscribed = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM saved_searches
                WHERE owner_subject = ? AND enabled AND email_enabled)
                """, Boolean.class, owner));
        jdbc.update("""
                UPDATE email_settings SET suppression = CASE WHEN suppression IN ('COMPLAINT','UNSUBSCRIBED')
                    THEN NULL ELSE suppression END, next_digest_at = CASE WHEN ? THEN next_digest_at ELSE ? END
                WHERE owner_subject = ?
                """, previouslySubscribed && setting.suppression() == null,
                ts(DigestSchedule.next(clock.instant())), owner);
    }

    @Transactional
    public void synchronizeRecipient(String owner, String verifiedEmail) {
        requireEmail(verifiedEmail);
        List<Setting> settings = locked(owner);
        if (settings.isEmpty()) return;
        if (!settings.getFirst().recipient().equals(verifiedEmail)) {
            invalidate(owner);
            jdbc.update("""
                    UPDATE email_settings SET recipient = ?, suppression = CASE WHEN suppression IN ('BOUNCE','PROVIDER')
                    THEN NULL ELSE suppression END WHERE owner_subject = ?
                    """, verifiedEmail, owner);
        }
    }

    @Transactional
    public void searchChanged(String owner, UUID searchId, boolean cancelCandidates) {
        if (locked(owner).isEmpty()) return;
        invalidate(owner);
        if (cancelCandidates) jdbc.update("""
                UPDATE email_candidates c SET state = 'CANCELLED', digest_id = NULL
                FROM job_alerts a WHERE c.alert_id = a.id AND a.saved_search_id = ?
                AND c.state = 'WAITING'
                """, searchId);
    }

    /** Caller holds the settings lock. Sending/ambiguous rows remain reserved for reconciliation. */
    void invalidate(String owner) {
        jdbc.update("UPDATE email_settings SET version = version + 1 WHERE owner_subject = ?", owner);
        jdbc.update("""
                UPDATE email_digests SET state = CASE WHEN first_attempt_at IS NULL THEN 'CANCELLED' ELSE 'UNCERTAIN' END,
                    lease_owner = NULL, lease_expires_at = NULL, error_code = 'preferences_changed'
                WHERE owner_subject = ? AND state IN ('PENDING','SENDING')
                """, owner);
        jdbc.update("""
                UPDATE email_candidates c SET state = 'WAITING', digest_id = NULL
                FROM email_digests d WHERE c.digest_id = d.id AND d.owner_subject = ?
                    AND d.state = 'CANCELLED' AND c.state = 'RESERVED'
                """, owner);
    }

    @Transactional
    public void unsubscribe(String token) {
        var owners = jdbc.query("""
                SELECT t.owner_subject FROM email_unsubscribe_tokens t JOIN email_settings s
                ON s.owner_subject = t.owner_subject AND s.recipient = t.recipient
                WHERE token_hash = ? AND expires_at > ?
                """, (rs, row) -> rs.getString(1), hash(token), ts(clock.instant()));
        if (owners.isEmpty()) throw new EntityNotFoundException("Unsubscribe link expired or invalid");
        String owner = owners.getFirst();
        lock(owner);
        invalidate(owner);
        jdbc.update("UPDATE saved_searches SET email_enabled = FALSE, email_enabled_at = NULL, version = version + 1 WHERE owner_subject = ?", owner);
        jdbc.update("UPDATE email_settings SET suppression = 'UNSUBSCRIBED' WHERE owner_subject = ?", owner);
        jdbc.update("UPDATE email_candidates SET state = 'CANCELLED' WHERE owner_subject = ? AND state = 'WAITING'", owner);
    }

    @Transactional(readOnly = true)
    public boolean validToken(String token) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM email_unsubscribe_tokens t JOIN email_settings s
                ON s.owner_subject = t.owner_subject AND s.recipient = t.recipient
                WHERE token_hash = ? AND expires_at > ?)
                """, Boolean.class, hash(token), ts(clock.instant())));
    }

    String issueToken(String owner, String recipient) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO email_unsubscribe_tokens VALUES (?, ?, ?, ?)", hash(token), owner, recipient,
                ts(clock.instant().plus(Duration.ofDays(180))));
        return token;
    }

    @Transactional(readOnly = true)
    public SettingsView view(String owner, String email) {
        var rows = jdbc.query("SELECT * FROM email_settings WHERE owner_subject = ?", (rs, row) ->
                new SettingsView(rs.getString("recipient"), "America/New_York", "09:00",
                        rs.getTimestamp("next_digest_at").toInstant(), properties.enabled(), rs.getString("suppression")), owner);
        SettingsView view = rows.isEmpty() ? new SettingsView(email, "America/New_York", "09:00", null,
                properties.enabled(), null) : rows.getFirst();
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM saved_searches WHERE owner_subject = ? AND enabled AND email_enabled)
                """, Boolean.class, owner));
        return new SettingsView(view.recipient(), view.timeZone(), view.localTime(),
                active && view.suppression() == null ? view.nextDigestAt() : null,
                view.serviceAvailable(), view.suppression());
    }

    Setting lock(String owner) {
        var rows = locked(owner);
        if (rows.isEmpty()) throw new EntityNotFoundException("Email settings not found");
        return rows.getFirst();
    }
    private List<Setting> locked(String owner) {
        return jdbc.query("SELECT * FROM email_settings WHERE owner_subject = ? FOR UPDATE", (rs, row) ->
                new Setting(owner, rs.getString("recipient"), rs.getTimestamp("next_digest_at").toInstant(),
                        rs.getString("suppression"), rs.getLong("version")), owner);
    }
    static void requireEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 320 || email.contains("\r")
                || email.contains("\n") || !email.contains("@"))
            throw new IllegalArgumentException("A verified sign-in email is required");
    }
    static String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid unsubscribe token");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    static Timestamp ts(Instant instant) { return Timestamp.from(instant); }
    record Setting(String owner, String recipient, Instant next, String suppression, long version) {}
    public record SettingsView(String recipient, String timeZone, String localTime, Instant nextDigestAt,
            boolean serviceAvailable, String suppression) {}
}
