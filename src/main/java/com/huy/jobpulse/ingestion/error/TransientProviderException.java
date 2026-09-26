package com.huy.jobpulse.ingestion.error;

public class TransientProviderException extends ProviderException {
    public TransientProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
