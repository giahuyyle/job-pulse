package com.huy.jobpulse.admin.application;

import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AdminEventService {
    private final JobEventRepository events;
    private final Clock clock;

    public AdminEventService(JobEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public JobEvent requestRetry(UUID id) {
        JobEvent event = events.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Event not found: " + id));
        event.requestPublishRetry(clock.instant());
        return event;
    }
}
