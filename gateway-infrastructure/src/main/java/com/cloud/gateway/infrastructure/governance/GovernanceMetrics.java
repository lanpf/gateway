package com.cloud.gateway.infrastructure.governance;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Outcome;

/** Fixed tag vocabulary only: never include request identities, routes, keys or exception messages. */
final class GovernanceMetrics {
    enum Lookup { FRESH_HIT, MISS, BYPASS, STALE_HIT, STALE_MISS }
    private enum Admission { ALLOWED, REJECTED }
    private enum Store { STORED, REJECTED }
    private static final String OUTCOME = "outcome";
    private static final String CACHE_LOOKUP = "gateway.governance.cache.lookup";
    private static final String CACHE_STORE = "gateway.governance.cache.store";
    private static final String CIRCUIT_ADMISSION = "gateway.governance.circuit.admission";
    private static final String CIRCUIT_COMPLETION = "gateway.governance.circuit.completion";
    private static final String CIRCUIT_DURATION = "gateway.governance.circuit.duration";
    private final Map<Lookup, Counter> lookups = new EnumMap<>(Lookup.class);
    private final Map<Outcome, Counter> completions = new EnumMap<>(Outcome.class);
    private final Map<Outcome, Timer> durations = new EnumMap<>(Outcome.class);
    private final Map<Store, Counter> stores = new EnumMap<>(Store.class);
    private final Map<Admission, Counter> admissions = new EnumMap<>(Admission.class);

    GovernanceMetrics(MeterRegistry registry) {
        if (registry == null) {
            return;
        }
        for (Lookup value : Lookup.values()) {
            lookups.put(value, registry.counter(CACHE_LOOKUP, OUTCOME, value.name().toLowerCase(Locale.ROOT)));
        }
        for (Outcome value : Outcome.values()) {
            String label = value.name().toLowerCase(Locale.ROOT);
            completions.put(value, registry.counter(CIRCUIT_COMPLETION, OUTCOME, label));
            durations.put(value, registry.timer(CIRCUIT_DURATION, OUTCOME, label));
        }
        for (Store value : Store.values()) {
            stores.put(value, registry.counter(CACHE_STORE, OUTCOME, value.name().toLowerCase(Locale.ROOT)));
        }
        for (Admission value : Admission.values()) {
            admissions.put(value, registry.counter(CIRCUIT_ADMISSION, OUTCOME, value.name().toLowerCase(Locale.ROOT)));
        }
    }

    void lookup(Lookup outcome) { increment(lookups.get(outcome)); }
    void store(boolean stored) { increment(stores.get(stored ? Store.STORED : Store.REJECTED)); }
    void admission(boolean allowed) { increment(admissions.get(allowed ? Admission.ALLOWED : Admission.REJECTED)); }

    void complete(Outcome outcome, long nanos) {
        increment(completions.get(outcome));
        Timer timer = durations.get(outcome);
        if (timer != null) {
            timer.record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
