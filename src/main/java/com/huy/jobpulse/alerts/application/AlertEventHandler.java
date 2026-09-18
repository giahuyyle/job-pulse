package com.huy.jobpulse.alerts.application;

import com.huy.jobpulse.events.EventConsumptionStore;
import com.huy.jobpulse.events.JobEventEnvelope;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.infrastructure.JobMatchSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AlertEventHandler {

    public static final String CONSUMER_NAME = "jobpulse-alerts";
    private static final String SAVED_SEARCH_FILTERS = JobMatchSql.predicates(
            "j",
            "s.query",
            "s.company",
            "s.source",
            "s.remote_policy",
            "s.location"
    );

    private final EventConsumptionStore consumptions;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AlertEventHandler(
            EventConsumptionStore consumptions,
            NamedParameterJdbcTemplate jdbc,
            Clock clock
    ) {
        this.consumptions = consumptions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void process(JobEventEnvelope event) {
        if (!consumptions.begin(CONSUMER_NAME, event.eventId())) {
            return;
        }
        if (event.eventType() != JobEventType.CREATED) {
            return;
        }
        for (UUID searchId : matchingSearches(event)) {
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
                            .addValue("jobPostingId", event.jobId(), Types.OTHER)
                            .addValue(
                                    "createdAt",
                                    clock.instant().atOffset(ZoneOffset.UTC),
                                    Types.TIMESTAMP_WITH_TIMEZONE
                            )
            );
        }
    }

    private List<UUID> matchingSearches(JobEventEnvelope event) {
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
                        .addValue("jobPostingId", event.jobId(), Types.OTHER)
                        .addValue(
                                "eventCreatedAt",
                                event.occurredAt().atOffset(ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        ),
                (result, row) -> result.getObject("id", UUID.class)
        );
    }
}
