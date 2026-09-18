package com.huy.jobpulse.alerts.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertMatcher {

    private final AlertMatcherService service;

    public AlertMatcher(AlertMatcherService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.alerts.match-delay-ms:60000}",
            initialDelayString = "${jobpulse.alerts.match-initial-delay-ms:60000}"
    )
    public void matchCreatedJobs() {
        service.processBatch();
    }
}
