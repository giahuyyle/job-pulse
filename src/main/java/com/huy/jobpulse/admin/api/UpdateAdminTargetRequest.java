package com.huy.jobpulse.admin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateAdminTargetRequest(
        Boolean enabled,
        @Min(15) @Max(1440) Integer intervalMinutes
) {}
