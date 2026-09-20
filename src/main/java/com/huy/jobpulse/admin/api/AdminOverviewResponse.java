package com.huy.jobpulse.admin.api;

import java.time.Instant;

public record AdminOverviewResponse(long totalBoards, long activeBoards,
        long totalPostings, long openPostings, long failedRunsLast24Hours,
        long unpublishedEvents, long deadLetterMessages,
        Instant lastSuccessfulRunAt) {}
