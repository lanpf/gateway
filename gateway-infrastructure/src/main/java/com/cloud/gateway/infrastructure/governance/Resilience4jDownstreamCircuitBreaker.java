package com.cloud.gateway.infrastructure.governance;

import com.cloud.gateway.application.governance.DownstreamCircuitBreaker;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.util.Assert;
import io.micrometer.core.instrument.MeterRegistry;

/** Bounded local Resilience4j state, keyed by route and effective circuit settings, not snapshot version.
 * Relevant policy changes select independent state (or reuse a retained identical configuration);
 * in-flight permits retain their original instance. Fallback-only changes preserve history.
 * Eviction under configured capacity pressure discards that circuit's history.
 */
public final class Resilience4jDownstreamCircuitBreaker implements DownstreamCircuitBreaker {
    private final Cache<CircuitKey, CircuitBreaker> circuits;
    private final GovernanceMetrics metrics;

    public Resilience4jDownstreamCircuitBreaker(long maximumCircuits) {
        this(maximumCircuits, null);
    }

    public Resilience4jDownstreamCircuitBreaker(long maximumCircuits, MeterRegistry registry) {
        Assert.isTrue(maximumCircuits > 0, "maximumCircuits must be positive");
        circuits = Caffeine.newBuilder().maximumSize(maximumCircuits).build();
        metrics = new GovernanceMetrics(registry);
    }

    @Override
    public Permit acquire(String routeId, CircuitBreakerPolicy policy) {
        Assert.hasText(routeId, "routeId must have text");
        Objects.requireNonNull(policy);
        CircuitKey key = new CircuitKey(routeId, policy.failureRateThreshold(), policy.slowCallRateThreshold(),
                policy.slowCallDuration(), policy.minimumCalls(), policy.slidingWindowSize(),
                policy.halfOpenCalls(), policy.waitDuration());
        CircuitBreaker circuit = circuits.get(key, ignored -> CircuitBreaker.of(routeId,
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(policy.failureRateThreshold())
                        .slowCallRateThreshold(policy.slowCallRateThreshold())
                        .slowCallDurationThreshold(policy.slowCallDuration())
                        .minimumNumberOfCalls(policy.minimumCalls())
                        .slidingWindowSize(policy.slidingWindowSize())
                        .permittedNumberOfCallsInHalfOpenState(policy.halfOpenCalls())
                        .waitDurationInOpenState(policy.waitDuration()).build()));
        boolean allowed = circuit.tryAcquirePermission();
        metrics.admission(allowed);
        return new CircuitPermit(circuit, allowed, metrics);
    }

    private record CircuitKey(String routeId, int failureRate, int slowRate,
            java.time.Duration slowDuration, int minimumCalls, int windowSize, int halfOpenCalls,
            java.time.Duration waitDuration) { }

    private static final class CircuitPermit implements Permit {
        private final CircuitBreaker circuit;
        private final boolean allowed;
        private final long startedAt;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final GovernanceMetrics metrics;

        private CircuitPermit(CircuitBreaker circuit, boolean allowed, GovernanceMetrics metrics) {
            this.circuit = circuit;
            this.allowed = allowed;
            this.startedAt = System.nanoTime();
            this.metrics = metrics;
        }

        @Override
        public boolean allowed() { return allowed; }

        @Override
        public void complete(Outcome outcome) {
            Objects.requireNonNull(outcome);
            if (!allowed || !completed.compareAndSet(false, true)) {
                return;
            }
            long elapsed = Math.max(0, System.nanoTime() - startedAt);
            metrics.complete(outcome, elapsed);
            switch (outcome) {
                case SUCCESS -> circuit.onSuccess(elapsed, TimeUnit.NANOSECONDS);
                case UPSTREAM_FAILURE -> circuit.onError(elapsed, TimeUnit.NANOSECONDS,
                        new java.io.IOException("Downstream attempt failed"));
                case IGNORED -> circuit.releasePermission();
            }
        }
    }
}
