package com.huy.jobpulse.jobs.application;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobPostingRepository repository;

    public JobQueryService(JobPostingRepository repository) {
        this.repository = repository;
    }

    public Page<JobPosting> findAll(Pageable pageable) {
        return repository.findAll(pageable);
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