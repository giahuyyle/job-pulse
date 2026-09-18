package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobSource;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionTargetService {

    private final IngestionTargetRepository repository;
    private final JobSourceRegistry sourceRegistry;
    private final Clock clock;

    public IngestionTargetService(
            IngestionTargetRepository repository,
            JobSourceRegistry sourceRegistry,
            Clock clock
    ) {
        this.repository = repository;
        this.sourceRegistry = sourceRegistry;
        this.clock = clock;
    }

    @Transactional
    public IngestionTarget create(CreateIngestionTargetRequest request) {
        String sourceAccount = request.sourceAccount().strip();
        sourceRegistry.validate(request.source(), sourceAccount);

        if (repository.existsBySourceAndSourceAccount(
                request.source(),
                sourceAccount
        )) {
            throw duplicate(request.source().name(), sourceAccount);
        }

        IngestionTarget target = IngestionTarget.create(
                request.source(),
                sourceAccount,
                request.company(),
                request.careersUrl(),
                request.resolvedIntervalMinutes(),
                clock.instant()
        );
        try {
            return repository.saveAndFlush(target);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(request.source().name(), sourceAccount);
        }
    }

    @Transactional(readOnly = true)
    public List<IngestionTarget> findAll() {
        return repository.findAllByOrderByCompanyAsc();
    }

    @Transactional
    public IngestionTarget setEnabled(UUID id, boolean enabled) {
        IngestionTarget target = require(id);
        target.setEnabled(enabled, clock.instant());
        return target;
    }

    private IngestionTarget require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ingestion target not found: " + id
                ));
    }

    private static DuplicateIngestionTargetException duplicate(
            String source,
            String sourceAccount
    ) {
        return new DuplicateIngestionTargetException(
                "Ingestion target already exists: "
                        + source + "/" + sourceAccount
        );
    }
}
