package com.cloud.gateway.application.governance;

import com.cloud.gateway.domain.policy.bundle.ResolvedPolicy;
import com.cloud.gateway.domain.policy.capability.CachePolicy;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import com.cloud.gateway.domain.policy.capability.CacheRequestFacts;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DownstreamGovernanceCoordinatorTest {
    private static final CachePolicy CACHE = new CachePolicy(Duration.ofSeconds(10), Duration.ofSeconds(20), 128, Set.of());
    private static final CircuitBreakerPolicy CIRCUIT = new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
            2, 10, 1, Duration.ofSeconds(1), CircuitBreakerPolicy.Fallback.STALE_CACHE);
    private static final ResolvedPolicy POLICY = new ResolvedPolicy(List.of(), null, CIRCUIT, CACHE, null);
    private static final CachedRepresentation REPRESENTATION = new CachedRepresentation(200, Map.of(), new byte[]{1});
    private final RecordingCache cache = new RecordingCache();
    private final RecordingCircuits circuits = new RecordingCircuits();
    private final DownstreamGovernanceCoordinator coordinator = new DownstreamGovernanceCoordinator(cache, circuits);

    @Test
    void shouldLookupWithoutCircuitAdmissionAndPreserveEvidence() {
        CacheRequest request = request(false);
        cache.candidate = Optional.of(REPRESENTATION);
        DownstreamAdmissionOutput.CacheHit hit = assertInstanceOf(DownstreamAdmissionOutput.CacheHit.class,
                coordinator.prepare(request, POLICY));
        assertSame(REPRESENTATION, hit.representation());
        assertSame(request, cache.request);
        assertSame(CACHE, cache.policy);
        assertEquals(0, circuits.acquisitions);
    }

    @Test
    void shouldSkipSubjectCacheAndFallbackLookups() {
        CacheRequest request = request(true);
        cache.candidate = Optional.of(REPRESENTATION);
        assertTrue(execution(request, POLICY).stale(ResponseCache.UpstreamFailure.CONNECTION).isEmpty());
        assertNull(cache.request);
    }

    @Test
    void shouldPreserveRouteAdmissionAndCompleteOnlyOnceAfterHeaders() {
        DownstreamExecution execution = execution(request(false), POLICY);
        assertTrue(execution.allowed());
        assertEquals("catalog-api", circuits.routeId);
        execution.observeHeaders(503);
        execution.observeHeaders(200);
        assertTrue(execution.fail(ResponseCache.UpstreamFailure.CONNECTION).isEmpty());
        execution.finish();
        assertEquals(DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE, circuits.outcome);
        assertEquals(1, circuits.completions.get());
    }

    @Test
    void shouldReleaseUnobservedPermitExactlyOnceOnConcurrentTermination() {
        DownstreamExecution execution = execution(request(false), POLICY);
        CompletableFuture<?>[] completions = new CompletableFuture<?>[20];
        for (int index = 0; index < completions.length; index++) {
            completions[index] = CompletableFuture.runAsync(execution::finish);
        }
        CompletableFuture.allOf(completions).join();
        assertEquals(DownstreamCircuitBreaker.Outcome.IGNORED, circuits.outcome);
        assertEquals(1, circuits.completions.get());
    }

    @Test
    void shouldReturnOnlyClassifiedPreHeaderOriginFailures() {
        DownstreamExecution failed = execution(request(false), POLICY);
        assertEquals(ResponseCache.UpstreamFailure.TIMEOUT, failed.fail(ResponseCache.UpstreamFailure.TIMEOUT).orElseThrow());
        failed.finish();
        assertEquals(DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE, circuits.outcome);
        assertEquals(1, circuits.completions.get());
        DownstreamExecution local = execution(request(false), POLICY);
        assertTrue(local.fail(null).isEmpty());
        assertEquals(DownstreamCircuitBreaker.Outcome.IGNORED, circuits.outcome);
    }

    @Test
    void shouldRecheckStaleCandidateEveryTimeAndPreserveFallbackMode() {
        DownstreamExecution execution = execution(request(false), POLICY);
        cache.candidate = Optional.of(REPRESENTATION);
        assertTrue(execution.canFallback(503));
        cache.candidate = Optional.empty();
        assertTrue(execution.stale(ResponseCache.UpstreamFailure.SERVER_ERROR).isEmpty());
        assertEquals(2, cache.fallbacks);
        assertSame(CACHE, cache.policy);
        assertEquals(CircuitBreakerPolicy.Fallback.STALE_CACHE, cache.fallback);
        assertEquals(ResponseCache.UpstreamFailure.SERVER_ERROR, cache.failure);
    }

    @Test
    void shouldForwardUnchangedRepresentationAndCollectionDelayToCache() {
        CacheRequest request = request(false);
        DownstreamExecution execution = execution(request, POLICY);
        Duration delay = Duration.ofMillis(75);
        execution.store(REPRESENTATION, delay);
        assertSame(request, cache.request);
        assertSame(CACHE, cache.policy);
        assertSame(REPRESENTATION, cache.stored);
        assertEquals(delay, cache.delay);
    }

    @Test
    void shouldSupportDisabledCapabilitiesAndRefusedAdmission() {
        ResolvedPolicy disabled = new ResolvedPolicy(List.of(), null, null, null, null);
        DownstreamExecution execution = execution(request(false), disabled);
        assertTrue(execution.allowed());
        execution.observeHeaders(200);
        execution.finish();
        assertEquals(0, circuits.acquisitions);
        circuits.allowed = false;
        DownstreamAdmissionOutput.Fallback fallback = assertInstanceOf(DownstreamAdmissionOutput.Fallback.class,
                coordinator.prepare(request(false), POLICY));
        assertFalse(fallback.execution().allowed());
    }

    private DownstreamExecution execution(CacheRequest request, ResolvedPolicy policy) {
        return assertInstanceOf(DownstreamAdmissionOutput.Execute.class, coordinator.prepare(request, policy)).execution();
    }

    @Test
    void shouldBypassFreshCandidatesForNonGetCredentialsAndRequestBodies() {
        cache.candidate = Optional.of(REPRESENTATION);
        for (CacheRequest request : List.of(
                new CacheRequest("route", "endpoint", "app", null, "POST", "/read", null, Map.of(), true, false),
                new CacheRequest("route", "endpoint", "app", null, "GET", "/read", null,
                        Map.of("Authorization", List.of("secret")), true, false),
                new CacheRequest("route", "endpoint", "app", null, "GET", "/read", null,
                        Map.of(), true, false, Map.of(), false, true))) {
            DownstreamExecution execution = execution(request, POLICY);
            assertFalse(execution.canCapture(200));
            assertTrue(execution.stale(ResponseCache.UpstreamFailure.TIMEOUT).isEmpty());
        }
        assertNull(cache.request);
        assertEquals(3, circuits.acquisitions);
    }

    @Test
    void shouldNeverQueryStaleForErrorFallbackModeOrUnclassifiedFailure() {
        CircuitBreakerPolicy error = new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
                2, 10, 1, Duration.ofSeconds(1), CircuitBreakerPolicy.Fallback.ERROR);
        DownstreamExecution execution = execution(request(false),
                new ResolvedPolicy(List.of(), null, error, CACHE, null));
        cache.candidate = Optional.of(REPRESENTATION);
        assertTrue(execution.stale(ResponseCache.UpstreamFailure.TIMEOUT).isEmpty());
        cache.candidate = Optional.empty();
        assertTrue(execution(request(false), POLICY).stale(null).isEmpty());
        assertEquals(0, cache.fallbacks);
    }

    private static CacheRequest request(boolean subjectPresent) {
        return new CacheRequest("catalog-api", "READ", "app", "client", "GET", "/read%2Fitem", "a=2&a=1",
                Map.of("Accept-Language", List.of("en")), true, false,
                Map.of("X-Gateway-Variant", List.of("controlled")), subjectPresent);
    }

    private static final class RecordingCache implements ResponseCache {
        @Override
        public CacheRequestFacts requestFacts(CacheRequest request) {
            return new CacheRequestFacts(request.method(), request.authorized(), request.streaming(),
                    request.subjectPresent(), request.headers().containsKey("authorization")
                            || request.headers().containsKey("cookie"), request.bodyPresent(), false, false, false, false);
        }
        private Optional<CachedRepresentation> candidate = Optional.empty();
        private CacheRequest request;
        private CachePolicy policy;
        private CachedRepresentation stored;
        private Duration delay;
        private CircuitBreakerPolicy.Fallback fallback;
        private UpstreamFailure failure;
        private int fallbacks;

        public Optional<CachedRepresentation> lookup(CacheRequest request, CachePolicy policy) {
            this.request = request;
            this.policy = policy;
            return candidate;
        }

        public boolean store(CacheRequest request, CachePolicy policy, CachedRepresentation representation, Duration delay) {
            this.request = request;
            this.policy = policy;
            this.stored = representation;
            this.delay = delay;
            return true;
        }

        public Optional<CachedRepresentation> fallback(CacheRequest request, CachePolicy policy,
                CircuitBreakerPolicy.Fallback fallback, UpstreamFailure failure) {
            this.request = request;
            this.policy = policy;
            this.fallback = fallback;
            this.failure = failure;
            fallbacks++;
            return candidate;
        }
    }

    private static final class RecordingCircuits implements DownstreamCircuitBreaker {
        private int acquisitions;
        private String routeId;
        private boolean allowed = true;
        private volatile Outcome outcome;
        private final AtomicInteger completions = new AtomicInteger();

        public Permit acquire(String routeId, CircuitBreakerPolicy policy) {
            this.routeId = routeId;
            acquisitions++;
            return new Permit() {
                public boolean allowed() {
                    return allowed;
                }
                public void complete(Outcome value) {
                    outcome = value;
                    completions.incrementAndGet();
                }
            };
        }
    }
}
