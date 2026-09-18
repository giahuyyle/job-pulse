package com.huy.jobpulse.alerts.api;

import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSavedSearchRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 150) String query,
        @Size(max = 255) String company,
        JobSource source,
        RemotePolicy remotePolicy,
        @Size(max = 500) String location
) {
}
