package com.huy.jobpulse.ingestion.application;

public class DuplicateIngestionTargetException extends RuntimeException {

    public DuplicateIngestionTargetException(String message) {
        super(message);
    }
}
