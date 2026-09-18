package com.huy.jobpulse.alerts.infrastructure;

import com.huy.jobpulse.alerts.domain.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedSearchRepository
        extends JpaRepository<SavedSearch, UUID> {

    List<SavedSearch> findAllByOrderByCreatedAtDesc();
}
