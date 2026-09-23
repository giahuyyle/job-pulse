package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.observability.TraceContextBridge;
import com.huy.jobpulse.observability.TraceContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestionDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(
            IngestionDispatcher.class
    );
    private final IngestionRequestService requestService;
    private final IngestionPublisher publisher;
    private final TraceContextBridge traceContexts;

    @Autowired
    public IngestionDispatcher(
            IngestionRequestService requestService,
            IngestionPublisher publisher,
            TraceContextBridge traceContexts
    ) {
        this.requestService = requestService;
        this.publisher = publisher;
        this.traceContexts = traceContexts;
    }

    public IngestionDispatcher(IngestionRequestService requestService,
            IngestionPublisher publisher) {
        this.requestService = requestService;
        this.publisher = publisher;
        this.traceContexts = null;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.ingestion.dispatch-delay-ms:2000}",
            initialDelayString = "${jobpulse.ingestion.dispatch-initial-delay-ms:2000}"
    )
    public void dispatchUnpublished() {
        for (var requestId : requestService.findUnpublishedIds()) {
            TraceContextSnapshot snapshot = traceContexts == null
                    ? TraceContextSnapshot.EMPTY
                    : snapshot(requestId);
            try (var trace = traceContexts == null ? null
                    : traceContexts.continueTrace(snapshot,
                            "jobpulse.ingestion.dispatch")) {
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

    private TraceContextSnapshot snapshot(java.util.UUID requestId) {
        var request = requestService.require(requestId);
        return new TraceContextSnapshot(
                request.getTraceParent(),
                request.getTraceState(),
                request.getTraceBaggage()
        );
    }

}
