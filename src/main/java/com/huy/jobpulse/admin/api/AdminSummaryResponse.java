package com.huy.jobpulse.admin.api;

import java.time.Instant;

public record AdminSummaryResponse(
        long enabledTargets,
        long disabledTargets,
        long dueTargets,
        Instant lastIngestionAt,
        long pendingIngestionRequests,
        long runningIngestionRequests,
        long successfulRuns,
        long failedRuns,
        long failedRunsLast24h,
        long postingsCreated,
        long postingsUpdated,
        long postingsUnchanged,
        long postingsClosed,
        long rabbitQueueDepth,
        long deadLetterMessages,
        long unpublishedJobEvents,
        long kafkaPublishingFailures,
        Instant oldestUnpublishedEventAt
) {}
