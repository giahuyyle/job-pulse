package com.huy.jobpulse.jobs.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import com.huy.jobpulse.jobs.infrastructure.JobSearchRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.huy.jobpulse.observability.JobPulseMetrics;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobPostingRepository repository;
    private final JobSearchRepository searchRepository;
    private final JobPulseMetrics metrics;

    public JobQueryService(
            JobPostingRepository repository,
            JobSearchRepository searchRepository,
            JobPulseMetrics metrics
    ) {
        this.repository = repository;
        this.searchRepository = searchRepository;
        this.metrics = metrics;
    }

    public Page<JobSearchHit> search(JobSearchCriteria criteria) {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            return searchRepository.search(criteria);
        } catch (RuntimeException exception) {
            outcome = "failed";
            throw exception;
        } finally {
            metrics.recordSearch(System.nanoTime() - startedAt, outcome);
        }
    }

    public JobPosting require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Job not found: " + id
                        )
                );
    }
}
