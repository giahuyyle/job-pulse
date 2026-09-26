package com.huy.jobpulse.jobs.infrastructure;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<JobEvent> {

    @Query(value = """
            SELECT *
              FROM job_events
             WHERE publish_status = 'PENDING'
               AND next_attempt_at <= now()
             ORDER BY created_at, id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<JobEvent> claimUnpublished(int limit);

    @Query(value = """
            UPDATE job_events
               SET publish_status = 'PENDING', lease_owner = NULL,
                   lease_expires_at = NULL
             WHERE publish_status = 'PUBLISHING'
               AND lease_expires_at < :now
            """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int reclaimExpired(@org.springframework.data.repository.query.Param("now") Instant now);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM JobEvent e WHERE e.id = :id")
    java.util.Optional<JobEvent> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    long countByPublishedAtIsNull();

    @Query("SELECT min(event.createdAt) FROM JobEvent event "
            + "WHERE event.publishedAt IS NULL")
    Instant findOldestUnpublishedAt();

    long countByEventType(JobEventType eventType);

    long countByPublishedAtIsNullAndLastPublishErrorIsNotNull();

    long countByPublishedAtIsNullAndPublishAttemptsGreaterThanEqual(int attempts);

    long countByPublishStatus(String status);

    List<JobEvent> findAllByJobPostingIdOrderByCreatedAtDesc(UUID jobPostingId);
}
