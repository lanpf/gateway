package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.ingress.PreparationObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Ambiguous endpoint matching is a configuration defect, not a caller-visible distinction: it is
 * counted here so deployment alerting can see it while the response stays uniform.
 */
public final class MicrometerPreparationObserver implements PreparationObserver {
    static final String AMBIGUOUS_MATCH = "gateway.ingress.ambiguous_endpoint_match";

    private final Counter ambiguousMatch;

    public MicrometerPreparationObserver(MeterRegistry registry) {
        this.ambiguousMatch = Counter.builder(AMBIGUOUS_MATCH)
                .description("Endpoint definitions overlapping on the same request path")
                .register(registry);
    }

    @Override
    public void ambiguousMatch() {
        ambiguousMatch.increment();
    }
}
