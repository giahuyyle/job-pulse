package com.huy.jobpulse.admin.api;

import jakarta.validation.constraints.NotNull;

public record SetBoardEnabledRequest(@NotNull Boolean enabled) {}
