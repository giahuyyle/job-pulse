package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.jobs.domain.JobSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApproveDiscoveryRequest(
        @NotNull JobSource source,
        @NotBlank String sourceAccount,
        @Min(15) @Max(1440) Integer intervalMinutes
) {
    public int resolvedIntervalMinutes() {
        return intervalMinutes == null ? 60 : intervalMinutes;
    }
}
