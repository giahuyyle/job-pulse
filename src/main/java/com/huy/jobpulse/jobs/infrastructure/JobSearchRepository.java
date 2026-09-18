package com.huy.jobpulse.jobs.infrastructure;

import com.huy.jobpulse.jobs.application.JobSearchCriteria;
import com.huy.jobpulse.jobs.application.JobSearchHit;
import com.huy.jobpulse.jobs.application.JobSearchSort;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;

@Repository
public class JobSearchRepository {

    private static final String FILTERS = JobMatchSql.predicates(
            "j",
            ":query",
            ":company",
            ":source",
            ":remotePolicy",
            ":location"
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JobSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<JobSearchHit> search(JobSearchCriteria criteria) {
        MapSqlParameterSource parameters = parameters(criteria);
        String orderBy = criteria.sort() == JobSearchSort.RELEVANCE
                ? """
                    ts_rank(
                        j.search_vector,
                        websearch_to_tsquery('english', :query)
                    ) DESC,
                    j.first_seen_at DESC,
                    j.id DESC
                    """
                : "j.first_seen_at DESC, j.id DESC";
        String select = """
                SELECT j.id, j.title, j.company, j.location,
                       j.remote_policy, j.source, j.posted_at,
                       j.first_seen_at, j.apply_url
                  FROM job_postings j
                 WHERE j.status = 'ACTIVE'
                """ + FILTERS + " ORDER BY " + orderBy
                + " LIMIT :limit OFFSET :offset";
        List<JobSearchHit> content = jdbc.query(
                select,
                parameters,
                (result, row) -> new JobSearchHit(
                        result.getObject("id", java.util.UUID.class),
                        result.getString("title"),
                        result.getString("company"),
                        result.getString("location"),
                        RemotePolicy.valueOf(result.getString("remote_policy")),
                        JobSource.valueOf(result.getString("source")),
                        result.getTimestamp("posted_at") == null
                                ? null
                                : result.getTimestamp("posted_at").toInstant(),
                        result.getTimestamp("first_seen_at").toInstant(),
                        result.getString("apply_url")
                )
        );
        Long total = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM job_postings j
                 WHERE j.status = 'ACTIVE'
                """ + FILTERS,
                parameters,
                Long.class
        );
        return new PageImpl<>(
                content,
                PageRequest.of(criteria.page(), criteria.size()),
                total == null ? 0 : total
        );
    }

    private static MapSqlParameterSource parameters(
            JobSearchCriteria criteria
    ) {
        var filters = criteria.filters();
        return new MapSqlParameterSource()
                .addValue("query", filters.query(), Types.VARCHAR)
                .addValue("company", filters.company(), Types.VARCHAR)
                .addValue(
                        "source",
                        filters.source() == null ? null : filters.source().name(),
                        Types.VARCHAR
                )
                .addValue(
                        "remotePolicy",
                        filters.remotePolicy() == null
                                ? null
                                : filters.remotePolicy().name(),
                        Types.VARCHAR
                )
                .addValue("location", filters.location(), Types.VARCHAR)
                .addValue("limit", criteria.size(), Types.INTEGER)
                .addValue(
                        "offset",
                        (long) criteria.page() * criteria.size(),
                        Types.BIGINT
                );
    }
}
