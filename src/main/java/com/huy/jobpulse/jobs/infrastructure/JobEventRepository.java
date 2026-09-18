package com.huy.jobpulse.jobs.infrastructure;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {

    @Query(value = """
            SELECT *
              FROM job_events
             WHERE published_at IS NULL
             ORDER BY created_at, id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<JobEvent> claimUnpublished(int limit);

    long countByPublishedAtIsNull();

    @Query("SELECT min(event.createdAt) FROM JobEvent event "
            + "WHERE event.publishedAt IS NULL")
    Instant findOldestUnpublishedAt();

    long countByEventType(JobEventType eventType);
}
