package com.huy.jobpulse.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TraceContextBridge {

    private static final String TRACE_PARENT = "traceparent";
    private static final String TRACE_STATE = "tracestate";
    private static final String BAGGAGE = "baggage";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContextBridge(ObjectProvider<Tracer> tracer,
            ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    public TraceContextSnapshot capture() {
        Span current = tracer.currentSpan();
        if (current == null || current.isNoop()) {
            return TraceContextSnapshot.EMPTY;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(current.context(), carrier, Map::put);
        return new TraceContextSnapshot(
                carrier.get(TRACE_PARENT),
                carrier.get(TRACE_STATE),
                carrier.get(BAGGAGE)
        );
    }

    public ContinuedTrace continueTrace(
            TraceContextSnapshot snapshot,
            String spanName
    ) {
        if (snapshot == null || snapshot.isEmpty()) {
            return ContinuedTrace.NOOP;
        }
        Map<String, String> carrier = new HashMap<>();
        putIfPresent(carrier, TRACE_PARENT, snapshot.traceParent());
        putIfPresent(carrier, TRACE_STATE, snapshot.traceState());
        putIfPresent(carrier, BAGGAGE, snapshot.baggage());
        Span span = propagator.extract(carrier, Map::get)
                .name(spanName)
                .start();
        if (span.isNoop()) {
            return ContinuedTrace.NOOP;
        }
        return new ContinuedTrace(span, tracer.withSpan(span));
    }

    private static void putIfPresent(Map<String, String> carrier,
            String key, String value) {
        if (value != null && !value.isBlank()) {
            carrier.put(key, value);
        }
    }

    public static final class ContinuedTrace implements AutoCloseable {

        private static final ContinuedTrace NOOP = new ContinuedTrace(null, null);

        private final Span span;
        private final Tracer.SpanInScope scope;

        private ContinuedTrace(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        public void error(Throwable failure) {
            if (span != null) {
                span.error(failure);
            }
        }

        @Override
        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }
}
