package com.huy.jobpulse.events;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class EventConsumptionStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public EventConsumptionStore(
            NamedParameterJdbcTemplate jdbc,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean begin(String consumerName, UUID eventId) {
        return jdbc.update(
                """
                INSERT INTO job_event_consumptions (
                    consumer_name, event_id, processed_at
                ) VALUES (
                    :consumerName, :eventId, :processedAt
                )
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("consumerName", consumerName, Types.VARCHAR)
                        .addValue("eventId", eventId, Types.OTHER)
                        .addValue(
                                "processedAt",
                                clock.instant().atOffset(ZoneOffset.UTC),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
        ) == 1;
    }
}
