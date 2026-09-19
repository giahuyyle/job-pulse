package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.domain.IngestionRequest;
import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IngestionRequestRepository
        extends JpaRepository<IngestionRequest, UUID> {

    Optional<IngestionRequest>
    findFirstByIngestionTargetIdAndStatusInOrderByCreatedAtAsc(
            UUID targetId,
            List<IngestionRequestStatus> statuses
    );

    long countByStatus(IngestionRequestStatus status);

    Page<IngestionRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<IngestionRequest>
    findTop50ByPublishedAtIsNullAndStatusOrderByCreatedAtAsc(
            IngestionRequestStatus status
    );

    List<IngestionRequest>
    findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
            IngestionRequestStatus status,
            Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingestion_requests
               SET published_at = :publishedAt,
                   version = version + 1
             WHERE id = :id
               AND published_at IS NULL
            """, nativeQuery = true)
    int markPublished(
            @Param("id") UUID id,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingestion_requests
               SET status = 'RUNNING',
                   started_at = :startedAt,
                   finished_at = NULL,
                   attempt_count = attempt_count + 1,
                   last_error = NULL,
                   lease_until = :leaseUntil,
                   version = version + 1
             WHERE id = :id
               AND status = 'PENDING'
            """, nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("startedAt") Instant startedAt,
            @Param("leaseUntil") Instant leaseUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingestion_requests
               SET status = 'SUCCEEDED',
                   finished_at = :finishedAt,
                   lease_until = NULL,
                   last_error = NULL,
                   version = version + 1
             WHERE id = :id
               AND status = 'RUNNING'
            """, nativeQuery = true)
    int markSucceeded(
            @Param("id") UUID id,
            @Param("finishedAt") Instant finishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingestion_requests
               SET status = 'PENDING',
                   lease_until = NULL,
                   last_error = :lastError,
                   version = version + 1
             WHERE id = :id
               AND status = 'RUNNING'
            """, nativeQuery = true)
    int releaseForRetry(
            @Param("id") UUID id,
            @Param("lastError") String lastError
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingestion_requests
               SET status = 'FAILED',
                   finished_at = :finishedAt,
                   lease_until = NULL,
                   last_error = :lastError,
                   version = version + 1
             WHERE id = :id
               AND status = 'RUNNING'
            """, nativeQuery = true)
    int markFailed(
            @Param("id") UUID id,
            @Param("finishedAt") Instant finishedAt,
            @Param("lastError") String lastError
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingestion_requests
               SET status = 'PENDING',
                   published_at = NULL,
                   lease_until = NULL,
                   last_error = :lastError,
                   version = version + 1
             WHERE id = :id
               AND status = 'RUNNING'
               AND lease_until < :now
            """, nativeQuery = true)
    int recoverExpired(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("lastError") String lastError
    );
}
