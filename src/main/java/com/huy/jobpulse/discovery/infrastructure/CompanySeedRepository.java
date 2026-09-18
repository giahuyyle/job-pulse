package com.huy.jobpulse.discovery.infrastructure;

import com.huy.jobpulse.discovery.domain.CompanySeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanySeedRepository
        extends JpaRepository<CompanySeed, UUID> {

    Optional<CompanySeed> findByCareersUrl(String careersUrl);

    List<CompanySeed> findAllByOrderByCompanyNameAsc();
}
