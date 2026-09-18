package com.huy.jobpulse.jobs.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record JobSearchPageResponse(
        List<JobSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static JobSearchPageResponse from(Page<JobSummaryResponse> result) {
        return new JobSearchPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
