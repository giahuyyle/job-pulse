package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngestionRunRepository
        extends JpaRepository<IngestionRun, UUID> {

    List<IngestionRun> findAllBySourceAndSourceAccountOrderByStartedAtAsc(
            JobSource source,
            String sourceAccount
    );
}
