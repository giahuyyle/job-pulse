package com.huy.jobpulse.analytics;

import com.huy.jobpulse.events.EventConsumptionStore;
import com.huy.jobpulse.events.JobEventEnvelope;
import com.huy.jobpulse.jobs.domain.JobEventType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.ZoneOffset;

@Service
public class AnalyticsEventHandler {

    public static final String CONSUMER_NAME = "jobpulse-analytics";

    private final EventConsumptionStore consumptions;
    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsEventHandler(
            EventConsumptionStore consumptions,
            NamedParameterJdbcTemplate jdbc
    ) {
        this.consumptions = consumptions;
        this.jdbc = jdbc;
    }

    @Transactional
    public void process(JobEventEnvelope event) {
        if (!consumptions.begin(CONSUMER_NAME, event.eventId())) {
            return;
        }
        if (event.eventType() != JobEventType.CREATED
                && event.eventType() != JobEventType.CLOSED) {
            return;
        }
        jdbc.update(
                """
                INSERT INTO daily_job_stats (
                    day, source, event_type, count
                ) VALUES (
                    :day, :source, :eventType, 1
                )
                ON CONFLICT (day, source, event_type)
                DO UPDATE SET count = daily_job_stats.count + 1
                """,
                new MapSqlParameterSource()
                        .addValue(
                                "day",
                                event.occurredAt()
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate(),
                                Types.DATE
                        )
                        .addValue("source", event.source().name(), Types.VARCHAR)
                        .addValue(
                                "eventType",
                                event.eventType().name(),
                                Types.VARCHAR
                        )
        );
    }
}
