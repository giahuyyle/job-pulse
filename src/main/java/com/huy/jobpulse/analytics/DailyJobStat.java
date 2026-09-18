package com.huy.jobpulse.analytics;

import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;

import java.time.LocalDate;

public record DailyJobStat(
        LocalDate day,
        JobSource source,
        JobEventType eventType,
        long count
) {
}
