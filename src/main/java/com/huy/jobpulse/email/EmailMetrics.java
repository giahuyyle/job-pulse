package com.huy.jobpulse.email;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.huy.jobpulse.email.EmailPreferences.ts;

@Component
public class EmailMetrics {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EmailProperties properties;
    private final Map<String, AtomicLong> values = new HashMap<>();
    public EmailMetrics(JdbcTemplate jdbc, Clock clock, EmailProperties properties, MeterRegistry registry) {
        this.jdbc = jdbc; this.clock = clock; this.properties = properties;
        for (String name : List.of("pending", "uncertain", "failed", "suppressed", "oldest_age_seconds", "daily_remaining", "monthly_remaining", "quota_blocked", "enabled")) {
            var value = new AtomicLong(); values.put(name, value);
            Gauge.builder("jobpulse.email." + name, value, AtomicLong::get).register(registry);
        }
    }
    @Scheduled(scheduler = "emailTaskScheduler", fixedDelayString = "${jobpulse.email.metrics-delay-ms:60000}",
            initialDelayString = "${jobpulse.email.metrics-initial-delay-ms:10000}")
    public void refresh() {
        for (String state : List.of("PENDING", "UNCERTAIN", "FAILED"))
            values.get(state.toLowerCase(Locale.ROOT)).set(jdbc.queryForObject("SELECT count(*) FROM email_digests WHERE state = ?", Long.class, state));
        values.get("failed").set(jdbc.queryForObject("SELECT count(*) FROM email_digests WHERE state = 'FAILED' OR delivery_status IN ('FAILED','SUPPRESSED')", Long.class));
        values.get("suppressed").set(jdbc.queryForObject("SELECT count(*) FROM email_settings WHERE suppression IS NOT NULL", Long.class));
        var oldest = jdbc.queryForObject("SELECT min(created_at) FROM email_candidates WHERE state = 'WAITING'", java.sql.Timestamp.class);
        values.get("oldest_age_seconds").set(oldest == null ? 0 : Math.max(0, Duration.between(oldest.toInstant(), clock.instant()).toSeconds()));
        Instant day = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant month = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        values.get("daily_remaining").set(Math.max(0, properties.dailyLimit() - jdbc.queryForObject("SELECT count(DISTINCT digest_id) FROM email_budget_reservations WHERE NOT released AND reserved_at >= ?", Long.class, ts(day))));
        values.get("monthly_remaining").set(Math.max(0, properties.monthlyLimit() - jdbc.queryForObject("SELECT count(DISTINCT digest_id) FROM email_budget_reservations WHERE NOT released AND reserved_at >= ?", Long.class, ts(month))));
        values.get("quota_blocked").set(Boolean.TRUE.equals(jdbc.queryForObject("SELECT blocked_until > ? FROM email_budget WHERE id = 1", Boolean.class, ts(clock.instant()))) ? 1 : 0);
        values.get("enabled").set(properties.enabled() ? 1 : 0);
    }
}
