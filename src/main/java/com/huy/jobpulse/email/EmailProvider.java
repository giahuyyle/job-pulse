package com.huy.jobpulse.email;

import java.time.Duration;

public interface EmailProvider {
    Result send(String payload, String idempotencyKey);
    enum Outcome { ACCEPTED, TRANSIENT, QUOTA_DAILY, QUOTA_MONTHLY, PERMANENT, UNKNOWN }
    record Result(Outcome outcome, String providerId, String code, Duration retryAfter) {
        public static Result accepted(String id) { return new Result(Outcome.ACCEPTED, id, null, Duration.ZERO); }
    }
}
