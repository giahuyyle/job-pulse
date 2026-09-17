package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.jobs.domain.JobSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIngestionTargetRequest(
        @NotNull
        JobSource source,

        @NotBlank
        @Size(max = 160)
        String sourceAccount,

        @NotBlank
        @Size(max = 255)
        String company,

        String careersUrl,

        @Min(15)
        @Max(1440)
        Integer intervalMinutes
) {
    public int resolvedIntervalMinutes() {
        return intervalMinutes == null ? 60 : intervalMinutes;
    }
}
