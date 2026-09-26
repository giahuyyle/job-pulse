package com.huy.jobpulse.ingestion.error;

import java.time.Duration;

public class ProviderRateLimitException extends TransientProviderException {
    private final Duration retryAfter;

    public ProviderRateLimitException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
