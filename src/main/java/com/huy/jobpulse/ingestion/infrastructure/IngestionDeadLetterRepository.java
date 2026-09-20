package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionDeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestionDeadLetterRepository extends JpaRepository<IngestionDeadLetter, UUID> {
    Page<IngestionDeadLetter> findAllByOrderByFailedAtDesc(Pageable pageable);
    long countByReplayedAtIsNull();
}
