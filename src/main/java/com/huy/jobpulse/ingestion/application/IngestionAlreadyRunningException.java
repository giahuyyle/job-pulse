package com.huy.jobpulse.ingestion.application;

public class IngestionAlreadyRunningException extends RuntimeException {

    public IngestionAlreadyRunningException(String message) {
        super(message);
    }
}
