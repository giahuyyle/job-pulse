package com.huy.jobpulse.jobs.infrastructure;

import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPostingRepository
        extends JpaRepository<JobPosting, UUID> {

    Optional<JobPosting>
    findBySourceAndSourceAccountAndSourceJobId(
            JobSource source,
            String sourceAccount,
            String sourceJobId
    );

    List<JobPosting> findAllBySourceAndSourceAccountAndStatus(
            JobSource source,
            String sourceAccount,
            JobStatus status
    );

    boolean existsBySourceAndSourceAccount(
            JobSource source,
            String sourceAccount
    );

    long countBySourceAndSourceAccount(
            JobSource source,
            String sourceAccount
    );
}
