package com.huy.jobpulse.ingestion.error;

public abstract class ProviderException extends RuntimeException {
    protected ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
