package com.cloud.gateway.infrastructure.governance;

import com.cloud.gateway.application.governance.CacheRequest;
import com.cloud.gateway.application.governance.CachedRepresentation;
import com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Outcome;
import com.cloud.gateway.application.governance.ResponseCache.UpstreamFailure;
import com.cloud.gateway.domain.policy.capability.CachePolicy;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceMetricsTest {
    @Test
    void shouldRecordCacheActivityWithoutIdentityTags() {
        var registry = new SimpleMeterRegistry();
        try {
            var cache = new CaffeineResponseCache(4096, Clock.systemUTC(), registry);
            var request = new CacheRequest("secret-route", "private-endpoint", "app", "client", "GET", "/private", "token=x",
                    Map.of(), true, false);
            var policy = new CachePolicy(Duration.ofSeconds(5), Duration.ofSeconds(5), 128, Set.of());
            var payload = new CachedRepresentation(200, Map.of("Content-Type", List.of("text/plain")), new byte[]{1});
            cache.lookup(request, policy);
            cache.store(request, policy, payload);
            cache.lookup(request, policy);
            cache.lookup(request, null);
            cache.fallback(request, policy, CircuitBreakerPolicy.Fallback.STALE_CACHE, UpstreamFailure.TIMEOUT);
            cache.store(request, policy, new CachedRepresentation(500, Map.of(), new byte[0]));
            assertEquals(1, registry.get("gateway.governance.cache.lookup").tag("outcome", "miss").counter().count());
            assertEquals(1, registry.get("gateway.governance.cache.lookup").tag("outcome", "fresh_hit").counter().count());
            assertEquals(1, registry.get("gateway.governance.cache.lookup").tag("outcome", "bypass").counter().count());
            assertEquals(1, registry.get("gateway.governance.cache.lookup").tag("outcome", "stale_hit").counter().count());
            assertEquals(1, registry.get("gateway.governance.cache.store").tag("outcome", "stored").counter().count());
            assertEquals(1, registry.get("gateway.governance.cache.store").tag("outcome", "rejected").counter().count());
            assertNotNull(registry.get("gateway.governance.cache.entries").gauge());
            assertNotNull(registry.get("gateway.governance.cache.weight").gauge());
            registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> assertEquals("outcome", tag.getKey())));
        } finally {
            registry.close();
        }
    }

    @Test
    void shouldCountCircuitCompletionExactlyOnceAndNeverTagRoutes() {
        var registry = new SimpleMeterRegistry();
        try {
            var circuits = new Resilience4jDownstreamCircuitBreaker(10, registry);
            var policy = new CircuitBreakerPolicy(50, 100, Duration.ofSeconds(1),
                    1, 1, 1, Duration.ofSeconds(30), CircuitBreakerPolicy.Fallback.ERROR);
            com.cloud.gateway.application.governance.DownstreamCircuitBreaker.Permit permit = circuits.acquire("never-a-metric-tag", policy);
            permit.complete(Outcome.UPSTREAM_FAILURE);
            permit.complete(Outcome.IGNORED);
            assertFalse(circuits.acquire("never-a-metric-tag", policy).allowed());
            assertEquals(1, registry.get("gateway.governance.circuit.admission").tag("outcome", "allowed").counter().count());
            assertEquals(1, registry.get("gateway.governance.circuit.admission").tag("outcome", "rejected").counter().count());
            assertEquals(1, registry.get("gateway.governance.circuit.completion").tag("outcome", "upstream_failure").counter().count());
            assertEquals(0, registry.get("gateway.governance.circuit.completion").tag("outcome", "ignored").counter().count());
            assertEquals(1, registry.get("gateway.governance.circuit.duration").tag("outcome", "upstream_failure").timer().count());
            registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> assertEquals("outcome", tag.getKey())));
        } finally {
            registry.close();
        }
    }
}
