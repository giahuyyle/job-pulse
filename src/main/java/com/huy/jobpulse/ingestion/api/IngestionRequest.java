package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.jobs.domain.JobSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record IngestionRequest(
        @NotNull
        JobSource source,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{1,160}")
        String sourceAccount
) {
}
