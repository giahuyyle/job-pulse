package com.huy.jobpulse.alerts.application;

import com.huy.jobpulse.jobs.infrastructure.JobMatchSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AlertMatcherService {

    private static final int BATCH_SIZE = 50;
    private static final String SAVED_SEARCH_FILTERS = JobMatchSql.predicates(
            "j",
            "s.query",
            "s.company",
            "s.source",
            "s.remote_policy",
            "s.location"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AlertMatcherService(
            NamedParameterJdbcTemplate jdbc,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public int processBatch() {
        List<CreatedEvent> events = jdbc.query(
                """
                SELECT e.id, e.job_posting_id, e.created_at
                  FROM job_events e
                 WHERE e.processed_at IS NULL
                   AND e.event_type = 'CREATED'
                 ORDER BY e.created_at, e.id
                 LIMIT :limit
                   FOR UPDATE SKIP LOCKED
                """,
                new MapSqlParameterSource("limit", BATCH_SIZE),
                (result, row) -> new CreatedEvent(
                        result.getObject("id", UUID.class),
                        result.getObject("job_posting_id", UUID.class),
                        result.getTimestamp("created_at").toInstant()
                )
        );
        Instant processedAt = clock.instant();
        for (CreatedEvent event : events) {
            for (UUID searchId : matchingSearches(event)) {
                insertAlert(searchId, event.jobPostingId(), processedAt);
            }
            markProcessed(event.id(), processedAt);
        }
        return events.size();
    }

    private List<UUID> matchingSearches(CreatedEvent event) {
        String sql = """
                SELECT s.id
                  FROM saved_searches s
                  JOIN job_postings j ON j.id = :jobPostingId
                 WHERE s.enabled = TRUE
                   AND s.created_at < :eventCreatedAt
                   AND j.status = 'ACTIVE'
                """ + SAVED_SEARCH_FILTERS;
        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue(
                                "jobPostingId",
                                event.jobPostingId(),
                                Types.OTHER
                        )
                        .addValue(
                                "eventCreatedAt",
                                event.createdAt().atOffset(ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        ),
                (result, row) -> result.getObject("id", UUID.class)
        );
    }

    private void insertAlert(
            UUID searchId,
            UUID jobPostingId,
            Instant createdAt
    ) {
        jdbc.update(
                """
                INSERT INTO job_alerts (
                    id, saved_search_id, job_posting_id, created_at,
                    read_at, version
                ) VALUES (
                    :id, :searchId, :jobPostingId, :createdAt, NULL, 0
                )
                ON CONFLICT (saved_search_id, job_posting_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID(), Types.OTHER)
                        .addValue("searchId", searchId, Types.OTHER)
                        .addValue("jobPostingId", jobPostingId, Types.OTHER)
                        .addValue(
                                "createdAt",
                                createdAt.atOffset(ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
        );
    }

    private void markProcessed(UUID eventId, Instant processedAt) {
        jdbc.update(
                """
                UPDATE job_events
                   SET processed_at = :processedAt
                 WHERE id = :id
                   AND processed_at IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("id", eventId, Types.OTHER)
                        .addValue(
                                "processedAt",
                                processedAt.atOffset(ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
        );
    }

    private record CreatedEvent(
            UUID id,
            UUID jobPostingId,
            Instant createdAt
    ) {
    }
}
