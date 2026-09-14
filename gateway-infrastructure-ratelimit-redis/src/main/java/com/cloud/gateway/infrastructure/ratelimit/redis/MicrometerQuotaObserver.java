package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.application.quota.QuotaObserver;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitRule;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

/** Low-cardinality final rule outcomes, independent of request/identity values. */
@RequiredArgsConstructor
final class MicrometerQuotaObserver implements QuotaObserver {
    private static final String DECISIONS = "gateway.quota.decisions";
    private static final String DIMENSION_TAG = "dimension";
    private static final String OUTCOME_TAG = "outcome";
    private enum Outcome { ALLOWED, UNAVAILABLE, DENIED }
    private final MeterRegistry registry;

    @Override
    public void record(RateLimitRule rule, RateLimitDecision decision) {
        Outcome outcome = decision.allowed() ? Outcome.ALLOWED
                : decision.unavailable() ? Outcome.UNAVAILABLE : Outcome.DENIED;
        registry.counter(DECISIONS, DIMENSION_TAG, rule.dimension().name(),
                OUTCOME_TAG, outcome.name().toLowerCase(Locale.ROOT)).increment();
    }
}
