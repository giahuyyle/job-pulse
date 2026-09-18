package com.huy.jobpulse.events;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class JobEventCodec {

    private final ObjectMapper objectMapper;

    public JobEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(JobEventEnvelope event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize job event", exception);
        }
    }

    public JobEventEnvelope decode(String value) {
        try {
            JobEventEnvelope event = objectMapper.readValue(
                    value,
                    JobEventEnvelope.class
            );
            validate(event);
            return event;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Malformed job event", exception);
        }
    }

    private static void validate(JobEventEnvelope event) {
        if (event.schemaVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported job event schema: " + event.schemaVersion()
            );
        }
        if (event.eventId() == null
                || event.eventType() == null
                || event.jobId() == null
                || event.occurredAt() == null
                || event.source() == null
                || event.sourceAccount() == null
                || event.company() == null
                || event.title() == null
                || event.remotePolicy() == null) {
            throw new IllegalArgumentException("Job event is missing required fields");
        }
    }
}
