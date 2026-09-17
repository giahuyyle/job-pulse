package com.huy.jobpulse.ingestion.application;

public record IngestionResult(
        int discovered,
        int created,
        int updated,
        int unchanged,
        int closed
) {
    public IngestionResult {
        if (discovered < 0
                || created < 0
                || updated < 0
                || unchanged < 0
                || closed < 0) {
            throw new IllegalArgumentException("Ingestion counts must not be negative");
        }
        if (discovered != created + updated + unchanged) {
            throw new IllegalArgumentException(
                    "Ingestion outcomes must add up to discovered"
            );
        }
    }
}
