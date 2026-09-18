package com.huy.jobpulse.discovery.application;

public class DiscoveryAlreadyRunningException extends RuntimeException {

    public DiscoveryAlreadyRunningException() {
        super("Board discovery is already running");
    }
}
