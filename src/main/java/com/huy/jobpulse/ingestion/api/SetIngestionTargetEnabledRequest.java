package com.huy.jobpulse.ingestion.api;

import jakarta.validation.constraints.NotNull;

public record SetIngestionTargetEnabledRequest(
        @NotNull Boolean enabled
) {
}
