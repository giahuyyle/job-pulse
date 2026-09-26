package com.huy.jobpulse.observability;

import com.huy.jobpulse.events.OutboxLeaseService;
import com.huy.jobpulse.ingestion.application.IngestionRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupRecovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupRecovery.class);
    private final IngestionRequestService requests;
    private final OutboxLeaseService events;

    public StartupRecovery(IngestionRequestService requests, OutboxLeaseService events) {
        this.requests = requests;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int requestCount = requests.recoverExpired(3);
        int eventCount = events.reclaimExpired();
        LOGGER.atInfo().addKeyValue("ingestionRequestsReclaimed", requestCount)
                .addKeyValue("outboxEventsReclaimed", eventCount)
                .log("Startup recovery completed");
    }
}
