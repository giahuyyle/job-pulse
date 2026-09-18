package com.huy.jobpulse.ingestion.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class IngestionTargetScheduler {

    private final IngestionRequestService requestService;
    private final Clock clock;

    public IngestionTargetScheduler(
            IngestionRequestService requestService,
            Clock clock
    ) {
        this.requestService = requestService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.ingestion.poll-delay-ms:60000}",
            initialDelayString = "${jobpulse.ingestion.initial-delay-ms:60000}"
    )
    public void pollDueTargets() {
        requestService.enqueueDueTargets(clock.instant());
    }
}
