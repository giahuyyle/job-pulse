package com.huy.jobpulse.alerts.application;

import com.huy.jobpulse.alerts.api.CreateSavedSearchRequest;
import com.huy.jobpulse.alerts.api.UpdateSavedSearchRequest;
import com.huy.jobpulse.alerts.domain.SavedSearch;
import com.huy.jobpulse.alerts.infrastructure.SavedSearchRepository;
import com.huy.jobpulse.jobs.application.JobSearchFilters;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class SavedSearchService {

    private final SavedSearchRepository repository;
    private final Clock clock;

    public SavedSearchService(
            SavedSearchRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SavedSearch create(CreateSavedSearchRequest request) {
        JobSearchFilters filters = new JobSearchFilters(
                request.query(),
                request.company(),
                request.source(),
                request.remotePolicy(),
                request.location()
        );
        return repository.save(SavedSearch.create(
                request.name(),
                filters.query(),
                filters.company(),
                filters.source(),
                filters.remotePolicy(),
                filters.location(),
                clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public List<SavedSearch> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public SavedSearch update(UUID id, UpdateSavedSearchRequest request) {
        SavedSearch search = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Saved search not found: " + id
                ));
        if (request.namePresent()) {
            search.rename(request.name());
        }
        JobSearchFilters filters = new JobSearchFilters(
                request.queryPresent()
                        ? request.query()
                        : search.getQuery(),
                request.companyPresent()
                        ? request.company()
                        : search.getCompany(),
                request.sourcePresent()
                        ? request.source()
                        : search.getSource(),
                request.remotePolicyPresent()
                        ? request.remotePolicy()
                        : search.getRemotePolicy(),
                request.locationPresent()
                        ? request.location()
                        : search.getLocation()
        );
        search.apply(
                filters.query(),
                filters.company(),
                filters.source(),
                filters.remotePolicy(),
                filters.location()
        );
        if (request.enabledPresent()) {
            if (request.enabled() == null) {
                throw new IllegalArgumentException(
                        "enabled must not be null when provided"
                );
            }
            search.setEnabled(request.enabled());
        }
        return search;
    }
}
