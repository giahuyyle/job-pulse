package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IngestionTargetRepository
        extends JpaRepository<IngestionTarget, UUID> {

    boolean existsBySourceAndSourceAccount(
            JobSource source,
            String sourceAccount
    );

    List<IngestionTarget> findAllByOrderByCompanyAsc();

    List<IngestionTarget>
    findTop10ByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            Instant now
    );
}
