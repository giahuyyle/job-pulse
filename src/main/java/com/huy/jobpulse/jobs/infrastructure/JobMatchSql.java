package com.huy.jobpulse.jobs.infrastructure;

public final class JobMatchSql {

    private JobMatchSql() {
    }

    public static String predicates(
            String job,
            String query,
            String company,
            String source,
            String remotePolicy,
            String location
    ) {
        return """
                AND (%s IS NULL OR %s.search_vector @@
                    websearch_to_tsquery('english', %s))
                AND (%s IS NULL OR lower(%s.company) = lower(%s))
                AND (%s IS NULL OR %s.source = %s)
                AND (%s IS NULL OR %s.remote_policy = %s)
                AND (%s IS NULL OR strpos(
                    lower(coalesce(%s.location, '')),
                    lower(%s)
                ) > 0)
                """.formatted(
                query, job, query,
                company, job, company,
                source, job, source,
                remotePolicy, job, remotePolicy,
                location, job, location
        );
    }
}
