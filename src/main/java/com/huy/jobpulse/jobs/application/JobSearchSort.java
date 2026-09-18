package com.huy.jobpulse.jobs.application;

public enum JobSearchSort {
    NEWEST,
    RELEVANCE;

    public static JobSearchSort parse(String value) {
        if (value == null || value.isBlank()) {
            return NEWEST;
        }
        return switch (value.strip().toLowerCase()) {
            case "newest" -> NEWEST;
            case "relevance" -> RELEVANCE;
            default -> throw new IllegalArgumentException(
                    "sort must be newest or relevance"
            );
        };
    }
}
