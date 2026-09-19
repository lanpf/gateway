package com.cloud.gateway.infrastructure.governance;

import com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Outcome;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Resilience4jDownstreamCircuitBreakerTest {
    private final Resilience4jDownstreamCircuitBreaker circuits = new Resilience4jDownstreamCircuitBreaker(100);
    private final CircuitBreakerPolicy policy = new CircuitBreakerPolicy(50, 100, Duration.ofSeconds(1),
            2, 2, 1, Duration.ofSeconds(30), CircuitBreakerPolicy.Fallback.ERROR);

    @Test
    void shouldOpenAfterRealFailuresAndIsolateRoutes() {
        circuits.acquire("route", policy).complete(Outcome.UPSTREAM_FAILURE);
        circuits.acquire("route", policy).complete(Outcome.UPSTREAM_FAILURE);
        assertFalse(circuits.acquire("route", policy).allowed());
        assertTrue(circuits.acquire("other", policy).allowed());
    }

    @Test
    void shouldIgnoreAuthFailuresAndCancelledAttemptsAndDeduplicateCompletions() {
        for (int index = 0; index < 10; index++) {
            circuits.acquire("route", policy).complete(Outcome.IGNORED);
        }
        com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Permit first = circuits.acquire("route", policy);
        first.complete(Outcome.UPSTREAM_FAILURE);
        first.complete(Outcome.UPSTREAM_FAILURE);
        com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Permit second = circuits.acquire("route", policy);
        assertTrue(second.allowed());
        second.complete(Outcome.UPSTREAM_FAILURE);
        assertFalse(circuits.acquire("route", policy).allowed());
    }

    @Test
    void shouldPreserveHistoryAcrossEqualPoliciesAndFallbackOnlyChanges() {
        circuits.acquire("route", policy).complete(Outcome.UPSTREAM_FAILURE);
        var sameSettings = new CircuitBreakerPolicy(50, 100, Duration.ofSeconds(1),
                2, 2, 1, Duration.ofSeconds(30), CircuitBreakerPolicy.Fallback.STALE_CACHE);
        circuits.acquire("route", sameSettings).complete(Outcome.UPSTREAM_FAILURE);
        assertFalse(circuits.acquire("route", sameSettings).allowed());
    }

    @Test
    void shouldKeepInflightPermitsOnTheirOriginalPolicyInstance() {
        com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Permit first = circuits.acquire("route", policy);
        var changed = new CircuitBreakerPolicy(60, 100, Duration.ofSeconds(1),
                2, 2, 1, Duration.ofSeconds(30), CircuitBreakerPolicy.Fallback.ERROR);
        circuits.acquire("route", changed).complete(Outcome.SUCCESS);
        first.complete(Outcome.UPSTREAM_FAILURE);
        circuits.acquire("route", policy).complete(Outcome.UPSTREAM_FAILURE);
        assertFalse(circuits.acquire("route", policy).allowed());
        assertTrue(circuits.acquire("route", changed).allowed());
    }

    @Test
    void shouldRecordConcurrentDuplicateCompletionOnlyOnce() throws Exception {
        com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Permit first = circuits.acquire("route", policy);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 20; index++) {
                tasks.add(executor.submit(() -> first.complete(Outcome.UPSTREAM_FAILURE)));
            }
            for (java.util.concurrent.Future<?> task : tasks) {
                task.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Permit second = circuits.acquire("route", policy);
            assertTrue(second.allowed());
            second.complete(Outcome.UPSTREAM_FAILURE);
            assertFalse(circuits.acquire("route", policy).allowed());
        } finally {
            executor.shutdownNow();
        }
    }
}
