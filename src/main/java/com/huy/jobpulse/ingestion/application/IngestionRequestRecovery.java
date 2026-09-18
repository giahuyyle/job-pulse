package com.huy.jobpulse.ingestion.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestionRequestRecovery {

    private final IngestionRequestService requestService;

    public IngestionRequestRecovery(IngestionRequestService requestService) {
        this.requestService = requestService;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.ingestion.recovery-delay-ms:60000}",
            initialDelayString = "${jobpulse.ingestion.recovery-initial-delay-ms:60000}"
    )
    public void recoverExpiredLeases() {
        requestService.recoverExpired(IngestionWorker.MAX_ATTEMPTS);
    }
}
