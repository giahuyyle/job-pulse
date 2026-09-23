package com.huy.jobpulse.observability;

public record TraceContextSnapshot(
        String traceParent,
        String traceState,
        String baggage
) {

    public static final TraceContextSnapshot EMPTY = new TraceContextSnapshot(
            null, null, null);

    public boolean isEmpty() {
        return traceParent == null || traceParent.isBlank();
    }
}
