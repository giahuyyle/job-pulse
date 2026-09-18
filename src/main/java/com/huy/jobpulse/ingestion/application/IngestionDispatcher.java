package com.huy.jobpulse.ingestion.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestionDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(
            IngestionDispatcher.class
    );
    private final IngestionRequestService requestService;
    private final IngestionPublisher publisher;

    public IngestionDispatcher(
            IngestionRequestService requestService,
            IngestionPublisher publisher
    ) {
        this.requestService = requestService;
        this.publisher = publisher;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.ingestion.dispatch-delay-ms:2000}",
            initialDelayString = "${jobpulse.ingestion.dispatch-initial-delay-ms:2000}"
    )
    public void dispatchUnpublished() {
        for (var requestId : requestService.findUnpublishedIds()) {
            try {
                publisher.publish(requestId);
                requestService.markPublished(requestId);
            } catch (RuntimeException exception) {
                logger.warn(
                        "Could not publish ingestion request {}",
                        requestId,
                        exception
                );
                return;
            }
        }
    }

}
