package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.util.List;

public interface JobSourceClient {

    JobSource source();

    List<ExternalJob> fetchAll(String sourceAccount);
}
