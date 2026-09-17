package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class IngestionTargetScheduler {

    private static final Logger logger = LoggerFactory.getLogger(
            IngestionTargetScheduler.class
    );

    private final IngestionTargetService targetService;
    private final IngestionCoordinator ingestionCoordinator;
    private final Clock clock;

    public IngestionTargetScheduler(
            IngestionTargetService targetService,
            IngestionCoordinator ingestionCoordinator,
            Clock clock
    ) {
        this.targetService = targetService;
        this.ingestionCoordinator = ingestionCoordinator;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.ingestion.poll-delay-ms:60000}",
            initialDelayString = "${jobpulse.ingestion.initial-delay-ms:60000}"
    )
    public void pollDueTargets() {
        List<IngestionTarget> dueTargets = targetService.findDue(
                clock.instant()
        );
        for (IngestionTarget target : dueTargets) {
            poll(target);
        }
    }

    private void poll(IngestionTarget target) {
        try {
            ingestionCoordinator.ingest(
                    target.getSource(),
                    target.getSourceAccount()
            );
        } catch (IngestionAlreadyRunningException exception) {
            logger.debug(
                    "Skipping already-running ingestion target {}",
                    target.getId()
            );
            return;
        } catch (RuntimeException exception) {
            markFailed(target, exception);
            return;
        }

        try {
            targetService.markSucceeded(target.getId(), clock.instant());
        } catch (RuntimeException exception) {
            logger.error(
                    "Could not mark ingestion target {} successful",
                    target.getId(),
                    exception
            );
        }
    }

    private void markFailed(
            IngestionTarget target,
            RuntimeException ingestionFailure
    ) {
        try {
            targetService.markFailed(
                    target.getId(),
                    clock.instant(),
                    ingestionFailure.getMessage()
            );
        } catch (RuntimeException recordingFailure) {
            logger.error(
                    "Could not record failure for ingestion target {}",
                    target.getId(),
                    recordingFailure
            );
        }
    }
}
