package com.huy.jobpulse.email;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class DigestScheduleTest {
    @Test void easternCalendarFollowsBothDstTransitions() {
        assertThat(DigestSchedule.at(LocalDate.of(2026, 3, 7))).isEqualTo(Instant.parse("2026-03-07T14:00:00Z"));
        assertThat(DigestSchedule.at(LocalDate.of(2026, 3, 8))).isEqualTo(Instant.parse("2026-03-08T13:00:00Z"));
        assertThat(DigestSchedule.at(LocalDate.of(2026, 10, 31))).isEqualTo(Instant.parse("2026-10-31T13:00:00Z"));
        assertThat(DigestSchedule.at(LocalDate.of(2026, 11, 1))).isEqualTo(Instant.parse("2026-11-01T14:00:00Z"));
    }
    @Test void cutoffSchedulesTheNextFutureSlot() {
        assertThat(DigestSchedule.next(Instant.parse("2026-09-28T12:59:59Z"))).isEqualTo(Instant.parse("2026-09-28T13:00:00Z"));
        assertThat(DigestSchedule.next(Instant.parse("2026-09-28T13:00:00Z"))).isEqualTo(Instant.parse("2026-09-29T13:00:00Z"));
    }
}
