package com.huy.jobpulse.discovery.application;

public class DiscoveryFetchException extends RuntimeException {

    public DiscoveryFetchException(String message) {
        super(message);
    }

    public DiscoveryFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
