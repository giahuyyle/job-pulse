package com.huy.jobpulse.admin.api;

import java.time.Instant;

public record AdminSummaryResponse(
        long enabledTargets,
        long dueTargets,
        long pendingIngestionRequests,
        long runningIngestionRequests,
        long failedRunsLast24h,
        long unpublishedJobEvents,
        Instant oldestUnpublishedEventAt
) {}
