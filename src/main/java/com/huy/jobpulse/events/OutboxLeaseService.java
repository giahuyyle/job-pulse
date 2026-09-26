package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import com.huy.jobpulse.observability.JobPulseMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxLeaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxLeaseService.class);
    private final JobEventRepository events;
    private final Clock clock;
    private final JobPulseMetrics metrics;

    public OutboxLeaseService(JobEventRepository events, Clock clock, JobPulseMetrics metrics) {
        this.events = events;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public List<ClaimedEvent> claim(int limit) {
        Instant now = clock.instant();
        int reclaimed = events.reclaimExpired(now);
        if (reclaimed > 0) {
            metrics.recordOutboxLeaseReclaimed(reclaimed);
            LOGGER.atWarn().addKeyValue("reclaimed", reclaimed)
                    .log("Expired outbox publication leases reclaimed");
        }
        return events.claimUnpublished(limit).stream().map(event -> {
            UUID owner = UUID.randomUUID();
            event.claim(owner, now.plusSeconds(30));
            return new ClaimedEvent(event, owner);
        }).toList();
    }

    @Transactional
    public int reclaimExpired() {
        int reclaimed = events.reclaimExpired(clock.instant());
        if (reclaimed > 0) {
            metrics.recordOutboxLeaseReclaimed(reclaimed);
            LOGGER.atWarn().addKeyValue("reclaimed", reclaimed)
                    .log("Expired outbox publication leases reclaimed");
        }
        return reclaimed;
    }

    @Transactional
    public boolean published(UUID eventId, UUID owner) {
        JobEvent event = events.lockById(eventId).orElseThrow();
        if (!owned(event, owner)) return false;
        event.markPublished(clock.instant());
        return true;
    }

    @Transactional
    public boolean failed(UUID eventId, UUID owner, String reason) {
        JobEvent event = events.lockById(eventId).orElseThrow();
        if (!owned(event, owner)) return false;
        event.markPublishFailed(clock.instant(), reason);
        return true;
    }

    private static boolean owned(JobEvent event, UUID owner) {
        return "PUBLISHING".equals(event.getPublishStatus())
                && owner.equals(event.getLeaseOwner());
    }

    public record ClaimedEvent(JobEvent event, UUID owner) {}
}
