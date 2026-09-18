package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;

import java.util.List;

public interface JobSourceClient {

    JobSource source();

    void validateSourceAccount(String sourceAccount);

    List<ExternalJob> fetchAll(String sourceAccount);

    default List<ExternalJob> fetchAll(
            String sourceAccount,
            String company
    ) {
        return fetchAll(sourceAccount);
    }
}
