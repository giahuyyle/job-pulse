package com.huy.jobpulse.analytics;

import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DailyJobStatsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DailyJobStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DailyJobStat> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query(
                """
                SELECT day, source, event_type, count
                  FROM daily_job_stats
                 WHERE day BETWEEN :from AND :to
                 ORDER BY day, source, event_type
                """,
                new MapSqlParameterSource()
                        .addValue("from", from, Types.DATE)
                        .addValue("to", to, Types.DATE),
                (result, row) -> new DailyJobStat(
                        result.getObject("day", LocalDate.class),
                        JobSource.valueOf(result.getString("source")),
                        JobEventType.valueOf(result.getString("event_type")),
                        result.getLong("count")
                )
        );
    }
}
