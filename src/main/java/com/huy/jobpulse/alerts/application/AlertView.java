package com.huy.jobpulse.alerts.application;

import com.huy.jobpulse.alerts.domain.JobAlert;
import com.huy.jobpulse.jobs.domain.JobPosting;

public record AlertView(JobAlert alert, JobPosting job) {
}
