package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;

public interface IngestionRunRepository
        extends JpaRepository<IngestionRun, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<IngestionRun> {

    List<IngestionRun> findAllBySourceAndSourceAccountOrderByStartedAtAsc(
            JobSource source,
            String sourceAccount
    );

    Page<IngestionRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<IngestionRun> findAllByStatusOrderByStartedAtDesc(
            IngestionRunStatus status, Pageable pageable
    );

    long countByStatusAndStartedAtGreaterThanEqual(
            IngestionRunStatus status, Instant since
    );

    long countByStatus(IngestionRunStatus status);

    IngestionRun findFirstByOrderByStartedAtDesc();

    IngestionRun findFirstByStatusOrderByCompletedAtDesc(IngestionRunStatus status);

    IngestionRun findFirstBySourceAndSourceAccountOrderByStartedAtDesc(
            JobSource source, String sourceAccount);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.unchanged), 0) from IngestionRun r")
    long sumUnchanged();

    Page<IngestionRun> findAllBySourceAndSourceAccountOrderByStartedAtDesc(
            JobSource source, String sourceAccount, Pageable pageable);
}
