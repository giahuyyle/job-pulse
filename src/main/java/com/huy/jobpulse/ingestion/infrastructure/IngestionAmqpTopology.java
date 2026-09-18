package com.huy.jobpulse.ingestion.infrastructure;

public final class IngestionAmqpTopology {

    public static final String EXCHANGE = "jobpulse.ingestion.exchange";
    public static final String QUEUE = "jobpulse.ingestion";
    public static final String ROUTING_KEY = "jobpulse.ingestion.request";
    public static final String DEAD_LETTER_EXCHANGE =
            "jobpulse.ingestion.dlx";
    public static final String DEAD_LETTER_QUEUE =
            "jobpulse.ingestion.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY =
            "jobpulse.ingestion.failed";

    private IngestionAmqpTopology() {
    }
}
