package com.huy.jobpulse.discovery.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BoardDiscoveryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(
            BoardDiscoveryScheduler.class
    );

    private final BoardDiscoveryService discoveryService;

    public BoardDiscoveryScheduler(BoardDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Scheduled(
            fixedDelayString = "${jobpulse.discovery.delay-ms:604800000}",
            initialDelayString = "${jobpulse.discovery.initial-delay-ms:604800000}"
    )
    public void rediscover() {
        try {
            discoveryService.runAll();
        } catch (DiscoveryAlreadyRunningException exception) {
            logger.debug("Skipping overlapping board discovery run");
        } catch (RuntimeException exception) {
            logger.error("Scheduled board discovery failed", exception);
        }
    }
}
