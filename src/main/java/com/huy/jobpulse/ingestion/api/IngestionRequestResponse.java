package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.domain.IngestionRequest;
import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record IngestionRequestResponse(
        UUID id,
        UUID ingestionTargetId,
        IngestionRequestStatus status,
        Instant createdAt,
        Instant publishedAt,
        Instant startedAt,
        Instant finishedAt,
        int attemptCount,
        String lastError
) {
    public static IngestionRequestResponse from(IngestionRequest request) {
        return new IngestionRequestResponse(
                request.getId(),
                request.getIngestionTargetId(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getPublishedAt(),
                request.getStartedAt(),
                request.getFinishedAt(),
                request.getAttemptCount(),
                request.getLastError()
        );
    }
}
