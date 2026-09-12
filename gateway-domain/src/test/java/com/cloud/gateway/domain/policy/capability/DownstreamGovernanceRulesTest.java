package com.cloud.gateway.domain.policy.capability;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DownstreamGovernanceRulesTest {
    private final DownstreamGovernanceRules rules = new DownstreamGovernanceRules();
    private final CachePolicy cache = new CachePolicy(Duration.ofSeconds(10), Duration.ofSeconds(20), 128, Set.of());

    @Test
    void shouldObserveOnlySelectedServerErrorsAsOriginFailures() {
        for (int status : new int[]{500, 502, 503, 504}) {
            assertEquals(DownstreamGovernanceRules.Observation.UPSTREAM_FAILURE, rules.observe(status));
        }
        for (int status : new int[]{100, 400, 401, 403, 429, 501, 505}) {
            assertEquals(DownstreamGovernanceRules.Observation.IGNORED, rules.observe(status));
        }
        for (int status : new int[]{200, 204, 302, 399}) {
            assertEquals(DownstreamGovernanceRules.Observation.SUCCESS, rules.observe(status));
        }
    }

    @Test
    void shouldCaptureOnlyAnonymousCredentialFreeNonStreamingSuccessfulGets() {
        CacheRequestFacts eligible = new CacheRequestFacts("GET", true, false, false, false, false, false, false, false, false);
        assertTrue(rules.canCapture(cache, eligible, 200));
        assertFalse(rules.canCapture(null, eligible, 200));
        assertFalse(rules.canCapture(cache, eligible, 204));
        for (CacheRequestFacts rejected : java.util.List.of(
                new CacheRequestFacts("POST", true, false, false, false, false, false, false, false, false),
                new CacheRequestFacts("GET", false, false, false, false, false, false, false, false, false),
                new CacheRequestFacts("GET", true, true, false, false, false, false, false, false, false),
                new CacheRequestFacts("GET", true, false, true, false, false, false, false, false, false),
                new CacheRequestFacts("GET", true, false, false, true, false, false, false, false, false),
                new CacheRequestFacts("GET", true, false, false, false, true, false, false, false, false),
                new CacheRequestFacts("GET", true, false, false, false, false, true, false, false, false),
                new CacheRequestFacts("GET", true, false, false, false, false, false, true, false, false),
                new CacheRequestFacts("GET", true, false, false, false, false, false, false, true, false),
                new CacheRequestFacts("GET", true, false, false, false, false, false, false, false, true))) {
            assertFalse(rules.canReadCache(cache, rejected));
            assertFalse(rules.canCapture(cache, rejected, 200));
        }
    }

    @Test
    void shouldRequireExplicitStaleModeAndClassifiedFailure() {
        assertTrue(rules.canFallback(CircuitBreakerPolicy.Fallback.STALE_CACHE, true));
        assertFalse(rules.canFallback(CircuitBreakerPolicy.Fallback.ERROR, true));
        assertFalse(rules.canFallback(CircuitBreakerPolicy.Fallback.STALE_CACHE, false));
        assertFalse(rules.canFallback(null, true));
    }

    @Test
    void shouldInterpretOnlyManagedStreamingTag() {
        assertTrue(rules.streaming(Set.of("streaming", "public")));
        assertFalse(rules.streaming(Set.of("public")));
    }

    @Test
    void shouldStoreOnlyBoundedSupportedUnrestrictedRepresentations() {
        CacheRequestFacts request = new CacheRequestFacts("GET", true, false, false, false, false, false, false, false, false);
        for (CacheResponseFacts.ContentKind kind : CacheResponseFacts.ContentKind.values()) {
            CacheResponseFacts response = new CacheResponseFacts(200, 128, kind, false, false, false, Set.of(), true);
            assertEquals(kind != CacheResponseFacts.ContentKind.OTHER, rules.canStore(cache, request, response));
        }
        for (CacheResponseFacts rejected : java.util.List.of(
                new CacheResponseFacts(201, 128, CacheResponseFacts.ContentKind.JSON, false, false, false, Set.of(), true),
                new CacheResponseFacts(200, 129, CacheResponseFacts.ContentKind.JSON, false, false, false, Set.of(), true),
                new CacheResponseFacts(200, 128, CacheResponseFacts.ContentKind.JSON, true, false, false, Set.of(), true),
                new CacheResponseFacts(200, 128, CacheResponseFacts.ContentKind.JSON, false, true, false, Set.of(), true),
                new CacheResponseFacts(200, 128, CacheResponseFacts.ContentKind.JSON, false, false, true, Set.of(), true),
                new CacheResponseFacts(200, 128, CacheResponseFacts.ContentKind.JSON, false, false, false, Set.of("unregistered"), true),
                new CacheResponseFacts(200, 128, CacheResponseFacts.ContentKind.JSON, false, false, false, Set.of(), false))) {
            assertFalse(rules.canStore(cache, request, rejected));
        }
    }

    @Test
    void shouldCompareActualVaryNamesWithPolicyInDomain() {
        CachePolicy supported = new CachePolicy(cache.ttl(), cache.staleWindow(), cache.maxBodyBytes(),
                Set.of("accept-language", "accept-encoding"));
        CacheRequestFacts request = new CacheRequestFacts("GET", true, false, false, false, false, false, false, false, false);
        for (Set<String> names : java.util.List.of(Set.<String>of(), Set.of("accept-language"),
                Set.of("accept-language", "accept-encoding"))) {
            assertTrue(rules.canStore(supported, request, new CacheResponseFacts(200, 1,
                    CacheResponseFacts.ContentKind.JSON, false, false, false, names, true)));
        }
        for (Set<String> names : java.util.List.of(Set.of("*"), Set.of("unknown"),
                Set.of("accept-language", "unknown"))) {
            assertFalse(rules.canStore(supported, request, new CacheResponseFacts(200, 1,
                    CacheResponseFacts.ContentKind.JSON, false, false, false, names, true)));
        }
    }

    @Test
    void shouldRejectNullVaryCollectionAndElementsWithDomainException() {
        assertThrows(com.cloud.gateway.domain.common.DomainException.class, () -> new CacheResponseFacts(
                200, 1, CacheResponseFacts.ContentKind.JSON, false, false, false, null, true));
        java.util.HashSet<String> names = new java.util.HashSet<>();
        names.add("accept-language");
        names.add(null);
        assertThrows(com.cloud.gateway.domain.common.DomainException.class, () -> new CacheResponseFacts(
                200, 1, CacheResponseFacts.ContentKind.JSON, false, false, false, names, true));
    }

    @Test
    void shouldDefensivelyCopyVaryNames() {
        java.util.HashSet<String> names = new java.util.HashSet<>(Set.of("accept-language"));
        CacheResponseFacts facts = new CacheResponseFacts(200, 1, CacheResponseFacts.ContentKind.JSON,
                false, false, false, names, true);
        names.clear();
        assertEquals(Set.of("accept-language"), facts.varyNames());
        assertThrows(UnsupportedOperationException.class, () -> facts.varyNames().add("other"));
    }
}
