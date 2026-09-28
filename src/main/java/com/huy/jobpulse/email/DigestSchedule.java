package com.huy.jobpulse.email;

import java.time.*;

public final class DigestSchedule {
    public static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private DigestSchedule() {}
    public static Instant next(Instant now) {
        ZonedDateTime local = now.atZone(EASTERN);
        Instant slot = at(local.toLocalDate());
        return slot.isAfter(now) ? slot : at(local.toLocalDate().plusDays(1));
    }
    public static Instant at(LocalDate date) {
        return date.atTime(9, 0).atZone(EASTERN).toInstant();
    }
    public static LocalDate date(Instant slot) { return slot.atZone(EASTERN).toLocalDate(); }
}
