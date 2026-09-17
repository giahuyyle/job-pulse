package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class JobIngestionWriter implements IngestionWriter {

    private final JobPostingRepository repository;
    private final Clock clock;

    public JobIngestionWriter(
            JobPostingRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public IngestionResult upsert(
            JobSource source,
            String sourceAccount,
            List<ExternalJob> jobs
    ) {
        Instant observedAt = clock.instant();
        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (ExternalJob externalJob : jobs) {
            JobPosting existing = repository
                    .findBySourceAndSourceAccountAndSourceJobId(
                            source,
                            sourceAccount,
                            externalJob.sourceJobId()
                    )
                    .orElse(null);

            if (existing == null) {
                repository.save(JobPosting.create(
                        source,
                        sourceAccount,
                        externalJob.sourceJobId(),
                        externalJob.company(),
                        externalJob.title(),
                        externalJob.location(),
                        externalJob.description(),
                        externalJob.employmentType(),
                        externalJob.remotePolicy(),
                        externalJob.applyUrl(),
                        externalJob.postedAt(),
                        observedAt
                ));
                created++;
                continue;
            }

            boolean changed = existing.refresh(
                    externalJob.company(),
                    externalJob.title(),
                    externalJob.location(),
                    externalJob.description(),
                    externalJob.employmentType(),
                    externalJob.remotePolicy(),
                    externalJob.applyUrl(),
                    externalJob.postedAt(),
                    observedAt
            );

            if (changed) {
                updated++;
            } else {
                unchanged++;
            }
        }

        return new IngestionResult(
                jobs.size(),
                created,
                updated,
                unchanged
        );
    }
}
