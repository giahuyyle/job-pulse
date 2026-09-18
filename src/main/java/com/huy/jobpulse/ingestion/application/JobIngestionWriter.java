package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobIngestionWriter implements IngestionWriter {

    private final JobPostingRepository repository;
    private final JobEventRepository eventRepository;
    private final IngestionRunRepository runRepository;
    private final Clock clock;

    public JobIngestionWriter(
            JobPostingRepository repository,
            JobEventRepository eventRepository,
            IngestionRunRepository runRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public IngestionResult apply(
            UUID runId,
            JobSource source,
            String sourceAccount,
            List<ExternalJob> jobs
    ) {
        Instant observedAt = clock.instant();
        IngestionRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ingestion run not found: " + runId
                ));
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int closed = 0;

        for (ExternalJob externalJob : jobs) {
            JobPosting existing = repository
                    .findBySourceAndSourceAccountAndSourceJobId(
                            source,
                            sourceAccount,
                            externalJob.sourceJobId()
                    )
                    .orElse(null);

            if (existing == null) {
                JobPosting createdPosting = repository.save(JobPosting.create(
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
                eventRepository.save(JobEvent.created(
                        createdPosting.getId(),
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

        Set<String> seenSourceJobIds = jobs.stream()
                .map(ExternalJob::sourceJobId)
                .collect(Collectors.toSet());
        List<JobPosting> activePostings = repository
                .findAllBySourceAndSourceAccountAndStatus(
                        source,
                        sourceAccount,
                        JobStatus.ACTIVE
                );
        for (JobPosting posting : activePostings) {
            if (!seenSourceJobIds.contains(posting.getSourceJobId())
                    && posting.recordMissing(observedAt)) {
                closed++;
            }
        }

        IngestionResult result = new IngestionResult(
                jobs.size(),
                created,
                updated,
                unchanged,
                closed
        );
        run.succeed(
                result.discovered(),
                result.created(),
                result.updated(),
                result.unchanged(),
                result.closed(),
                observedAt
        );
        return result;
    }

}
