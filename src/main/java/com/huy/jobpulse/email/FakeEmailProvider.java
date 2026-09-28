package com.huy.jobpulse.email;

import java.util.*;

/** Explicitly constructed for local previews/tests; never selected for live delivery. */
public final class FakeEmailProvider implements EmailProvider {
    private final Map<String, String> messages = new LinkedHashMap<>();
    @Override public synchronized Result send(String payload, String key) {
        messages.putIfAbsent(key, payload);
        return Result.accepted(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }
    public synchronized Map<String, String> messages() { return Map.copyOf(messages); }
}
