package com.huy.jobpulse.alerts.application;

import com.huy.jobpulse.alerts.domain.JobAlert;
import com.huy.jobpulse.alerts.infrastructure.JobAlertRepository;
import com.huy.jobpulse.alerts.infrastructure.SavedSearchRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final JobAlertRepository alertRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final JobPostingRepository jobRepository;
    private final Clock clock;

    public AlertService(
            JobAlertRepository alertRepository,
            SavedSearchRepository savedSearchRepository,
            JobPostingRepository jobRepository,
            Clock clock
    ) {
        this.alertRepository = alertRepository;
        this.savedSearchRepository = savedSearchRepository;
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AlertView> findAll(String ownerSubject, boolean unreadOnly) {
        List<UUID> searchIds = savedSearchRepository
                .findAllByOwnerSubjectOrderByCreatedAtDesc(ownerSubject).stream()
                .map(search -> search.getId()).toList();
        if (searchIds.isEmpty()) return List.of();
        List<JobAlert> alerts = unreadOnly
                ? alertRepository
                        .findAllBySavedSearchIdInAndReadAtIsNullOrderByCreatedAtDescIdDesc(searchIds)
                : alertRepository.findAllBySavedSearchIdInOrderByCreatedAtDescIdDesc(searchIds);
        return alerts.stream()
                .map(alert -> new AlertView(
                        alert,
                        jobRepository.findById(alert.getJobPostingId())
                                .orElseThrow()
                ))
                .toList();
    }

    @Transactional
    public AlertView markRead(String ownerSubject, UUID id) {
        JobAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Alert not found: " + id
                ));
        if (!savedSearchRepository.existsByIdAndOwnerSubject(
                alert.getSavedSearchId(), ownerSubject)) {
            throw new EntityNotFoundException("Alert not found: " + id);
        }
        alert.markRead(clock.instant());
        return new AlertView(
                alert,
                jobRepository.findById(alert.getJobPostingId()).orElseThrow()
        );
    }
}
