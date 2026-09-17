package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.util.UUID;

public interface RunRecorder {

    UUID start(JobSource source, String sourceAccount);

    void failIfRunning(UUID runId, String failureMessage);
}
