package com.huy.jobpulse.ingestion.application;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class IngestionLeaseHeartbeat {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionLeaseHeartbeat.class);
    private final IngestionRequestService requests;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ingestion-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public IngestionLeaseHeartbeat(IngestionRequestService requests) {
        this.requests = requests;
    }

    public LeaseHandle start(IngestionWork work) {
        var future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!requests.renewLease(work)) {
                    LOGGER.atWarn().addKeyValue("requestId", work.requestId())
                            .addKeyValue("leaseOwner", work.leaseOwner())
                            .log("Ingestion lease could not be renewed");
                }
            } catch (RuntimeException failure) {
                LOGGER.atWarn().addKeyValue("requestId", work.requestId())
                        .setCause(failure).log("Ingestion lease renewal failed");
            }
        }, 60, 60, TimeUnit.SECONDS);
        return () -> future.cancel(false);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    public interface LeaseHandle extends AutoCloseable {
        @Override void close();
    }
}
