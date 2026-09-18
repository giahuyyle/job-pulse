package com.huy.jobpulse.events;

import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobEventCodecTest {

    private final JobEventCodec codec = new JobEventCodec(new ObjectMapper());

    @Test
    void roundTripsVersionOneEnvelope() {
        JobEventEnvelope event = event(1);

        assertThat(codec.decode(codec.encode(event))).isEqualTo(event);
    }

    @Test
    void rejectsMalformedAndUnsupportedEvents() {
        assertThatThrownBy(() -> codec.decode("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed");
        assertThatThrownBy(() -> codec.decode(codec.encode(event(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    private static JobEventEnvelope event(int schemaVersion) {
        return new JobEventEnvelope(
                schemaVersion,
                UUID.randomUUID(),
                JobEventType.CREATED,
                UUID.randomUUID(),
                Instant.parse("2026-09-18T10:00:00Z"),
                JobSource.ASHBY,
                "example",
                "Example",
                "Backend Engineer",
                RemotePolicy.REMOTE
        );
    }
}
