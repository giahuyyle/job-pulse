package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.ingestion.domain.IngestionDeadLetter;
import java.time.Instant;
import java.util.UUID;

public record AdminDeadLetterResponse(UUID id, UUID requestId, UUID targetId,
        Instant failedAt, String errorMessage, String payload,
        Instant replayedAt, UUID replayRequestId) {
    public static AdminDeadLetterResponse from(IngestionDeadLetter value) {
        return new AdminDeadLetterResponse(value.getId(), value.getRequestId(),
                value.getTargetId(), value.getFailedAt(), value.getErrorMessage(),
                value.getPayload(), value.getReplayedAt(), value.getReplayRequestId());
    }
}
