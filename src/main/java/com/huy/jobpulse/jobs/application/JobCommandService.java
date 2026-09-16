package com.huy.jobpulse.jobs.application;

import com.huy.jobpulse.jobs.api.CreateJobRequest;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class JobCommandService {

    private final JobPostingRepository repository;
    private final Clock clock;

    public JobCommandService(
            JobPostingRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public JobPosting create(CreateJobRequest request) {
        boolean alreadyExists = repository
                .findBySourceAndSourceAccountAndSourceJobId(
                        request.source(),
                        request.sourceAccount(),
                        request.sourceJobId()
                )
                .isPresent();

        if (alreadyExists) {
            throw new DuplicateJobException(
                    "Job already exists for source identity: "
                            + request.source()
                            + "/"
                            + request.sourceAccount()
                            + "/"
                            + request.sourceJobId()
            );
        }

        Instant observedAt = clock.instant();

        JobPosting job = JobPosting.create(
                request.source(),
                request.sourceAccount(),
                request.sourceJobId(),
                request.company(),
                request.title(),
                request.location(),
                request.description(),
                request.employmentType(),
                request.remotePolicy(),
                request.applyUrl(),
                request.postedAt(),
                observedAt
        );

        return repository.save(job);
    }
}