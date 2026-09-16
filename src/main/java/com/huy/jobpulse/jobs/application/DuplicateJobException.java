package com.huy.jobpulse.jobs.application;

public class DuplicateJobException
        extends RuntimeException {

    public DuplicateJobException(String message) {
        super(message);
    }
}