package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class IngestionRunRecorder implements RunRecorder {

    private final IngestionRunRepository repository;
    private final Clock clock;

    public IngestionRunRecorder(
            IngestionRunRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(JobSource source, String sourceAccount) {
        return repository.save(IngestionRun.start(
                source,
                sourceAccount,
                clock.instant()
        )).getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failIfRunning(UUID runId, String failureMessage) {
        repository.findById(runId).ifPresent(run -> {
            if (run.getStatus() == IngestionRunStatus.RUNNING) {
                run.fail(failureMessage, clock.instant());
            }
        });
    }
}
