package com.huy.jobpulse.jobs.application;

public record JobSearchCriteria(
        JobSearchFilters filters,
        JobSearchSort sort,
        int page,
        int size
) {
    public JobSearchCriteria {
        if (filters == null) {
            throw new IllegalArgumentException("filters must not be null");
        }
        if (sort == null) {
            sort = JobSearchSort.NEWEST;
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100"
            );
        }
        if (sort == JobSearchSort.RELEVANCE && filters.query() == null) {
            throw new IllegalArgumentException(
                    "relevance sort requires a nonblank query"
            );
        }
    }
}
