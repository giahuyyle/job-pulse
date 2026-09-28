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
    private final com.huy.jobpulse.email.EmailPreferences email;

    public SavedSearchService(
            SavedSearchRepository repository,
            Clock clock,
            com.huy.jobpulse.email.EmailPreferences email
    ) {
        this.repository = repository;
        this.clock = clock;
        this.email = email;
    }

    @Transactional
    public SavedSearch create(String ownerSubject, CreateSavedSearchRequest request) {
        return create(ownerSubject, null, request);
    }

    @Transactional
    public SavedSearch create(String ownerSubject, String verifiedEmail, CreateSavedSearchRequest request) {
        boolean emailEnabled = Boolean.TRUE.equals(request.emailEnabled());
        if (emailEnabled) email.optIn(ownerSubject, verifiedEmail);
        JobSearchFilters filters = new JobSearchFilters(
                request.query(),
                request.company(),
                request.source(),
                request.remotePolicy(),
                request.location()
        );
        SavedSearch search = SavedSearch.create(
                ownerSubject,
                request.name(),
                filters.query(),
                filters.company(),
                filters.source(),
                filters.remotePolicy(),
                filters.location(),
                clock.instant()
        );
        search.setEmailEnabled(emailEnabled, clock.instant());
        return repository.save(search);
    }

    @Transactional(readOnly = true)
    public List<SavedSearch> findAll(String ownerSubject) {
        return repository.findAllByOwnerSubjectOrderByCreatedAtDesc(ownerSubject);
    }

    @Transactional
    public SavedSearch update(String ownerSubject, UUID id, UpdateSavedSearchRequest request) {
        return update(ownerSubject, null, id, request);
    }

    @Transactional
    public SavedSearch update(String ownerSubject, String verifiedEmail, UUID id, UpdateSavedSearchRequest request) {
        SavedSearch search = repository.findByIdAndOwnerSubject(id, ownerSubject)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Saved search not found: " + id
                ));
        if (request.emailEnabledPresent() && request.emailEnabled() == null)
            throw new IllegalArgumentException("emailEnabled must not be null when provided");
        if (Boolean.TRUE.equals(request.emailEnabled())) email.optIn(ownerSubject, verifiedEmail);
        if (request.emailEnabledPresent()) search.setEmailEnabled(request.emailEnabled(), clock.instant());
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
        repository.flush();
        email.searchChanged(ownerSubject, id, !search.isEnabled() || !search.isEmailEnabled());
        return search;
    }
}
