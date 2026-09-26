package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionDeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.UUID;

public interface IngestionDeadLetterRepository extends JpaRepository<IngestionDeadLetter, UUID> {
    Page<IngestionDeadLetter> findAllByOrderByFailedAtDesc(Pageable pageable);
    long countByReplayedAtIsNull();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM IngestionDeadLetter d WHERE d.id = :id")
    Optional<IngestionDeadLetter> lockById(@Param("id") UUID id);
}
