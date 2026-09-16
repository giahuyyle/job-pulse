package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateJobRequest(

        @NotNull
        JobSource source,

        @NotBlank
        @Size(max = 160)
        String sourceAccount,

        @NotBlank
        @Size(max = 255)
        String sourceJobId,

        @NotBlank
        @Size(max = 255)
        String company,

        @NotBlank
        @Size(max = 500)
        String title,

        @Size(max = 500)
        String location,

        String description,

        @Size(max = 80)
        String employmentType,

        @NotNull
        RemotePolicy remotePolicy,

        @NotBlank
        String applyUrl,

        Instant postedAt
) {
}