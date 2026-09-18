package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestionTargetRepository
        extends JpaRepository<IngestionTarget, UUID> {

    boolean existsBySourceAndSourceAccount(
            JobSource source,
            String sourceAccount
    );

    Optional<IngestionTarget> findBySourceAndSourceAccount(
            JobSource source,
            String sourceAccount
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select target from IngestionTarget target
            where target.source = :source
              and target.sourceAccount = :sourceAccount
            """)
    Optional<IngestionTarget> findLockedBySourceAndSourceAccount(
            @Param("source") JobSource source,
            @Param("sourceAccount") String sourceAccount
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select target from IngestionTarget target where target.id = :id")
    Optional<IngestionTarget> findLockedById(@Param("id") UUID id);

    List<IngestionTarget> findAllByOrderByCompanyAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<IngestionTarget>
    findTop10ByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            Instant now
    );
}
