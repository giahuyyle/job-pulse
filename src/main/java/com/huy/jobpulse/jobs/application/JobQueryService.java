package com.huy.jobpulse.jobs.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import com.huy.jobpulse.jobs.infrastructure.JobSearchRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobPostingRepository repository;
    private final JobSearchRepository searchRepository;

    public JobQueryService(
            JobPostingRepository repository,
            JobSearchRepository searchRepository
    ) {
        this.repository = repository;
        this.searchRepository = searchRepository;
    }

    public Page<JobSearchHit> search(JobSearchCriteria criteria) {
        return searchRepository.search(criteria);
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
