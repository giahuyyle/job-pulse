package com.huy.jobpulse.ingestion.application;

import java.util.UUID;

public interface IngestionPublisher {

    void publish(UUID requestId);
}
