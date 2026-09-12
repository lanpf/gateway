package com.cloud.gateway.infrastructure.governance;

import com.cloud.gateway.application.governance.CacheRequest;
import com.cloud.gateway.application.governance.CachedRepresentation;
import com.cloud.gateway.application.governance.ResponseCache.UpstreamFailure;
import com.cloud.gateway.domain.policy.capability.CachePolicy;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy.Fallback;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaffeineResponseCacheTest {
    private final MutableClock clock = new MutableClock();
    private final CaffeineResponseCache cache = new CaffeineResponseCache(1024 * 1024, clock);
    private final CachePolicy policy = new CachePolicy(Duration.ofSeconds(10), Duration.ofSeconds(5), 1024,
            Set.of("accept-language"));

    @Test
    void shouldExpireAbsolutelyAndOnlyServeStaleAfterAllowedUpstreamFailure() {
        CacheRequest request = request(Map.of());
        assertTrue(cache.store(request, policy, response(Map.of())));
        clock.advance(9);
        assertEquals(List.of("9"), cache.lookup(request, policy).orElseThrow().headers().get("Age"));
        clock.advance(1);
        assertTrue(cache.lookup(request, policy).isEmpty());
        assertTrue(cache.fallback(request, policy, Fallback.ERROR, UpstreamFailure.TIMEOUT).isEmpty());
        assertTrue(cache.fallback(request, policy, Fallback.STALE_CACHE, null).isEmpty());
        assertTrue(cache.fallback(request, policy, Fallback.STALE_CACHE, UpstreamFailure.TIMEOUT).isPresent());
        clock.advance(5);
        assertTrue(cache.fallback(request, policy, Fallback.STALE_CACHE, UpstreamFailure.TIMEOUT).isEmpty());
    }

    @Test
    void shouldRejectOriginalCredentialsConditionalRequestsAndUnauthorizedRequests() {
        for (String header : List.of("Authorization", "Cookie", "Range", "If-None-Match", "Cache-Control",
                "Content-Length", "Transfer-Encoding", "If-Range")) {
            CacheRequest request = request(Map.of(header, List.of("value")));
            assertFalse(cache.store(request, policy, response(Map.of())));
            assertTrue(cache.lookup(request, policy).isEmpty());
        }
        CacheRequest authorized = request(Map.of());
        assertTrue(cache.store(authorized, policy, response(Map.of())));
        CacheRequest unauthorized = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of(), false, false);
        assertTrue(cache.lookup(unauthorized, policy).isEmpty());
        assertTrue(cache.fallback(unauthorized, policy, Fallback.STALE_CACHE, UpstreamFailure.SERVER_ERROR).isEmpty());
        CacheRequest streaming = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of(), true, true);
        assertFalse(cache.store(streaming, policy, response(Map.of())));
        CacheRequest post = new CacheRequest("route", "endpoint", "app", "client", "POST", "/items", "q=1",
                Map.of(), true, false);
        assertFalse(cache.store(post, policy, response(Map.of())));
    }

    @Test
    void shouldRejectSensitiveStreamingAndUnsupportedVaryResponses() {
        for (Map<String, List<String>> headers : List.of(
                Map.of("Set-Cookie", List.of("sid=x")),
                Map.of("Cache-Control", List.of("PRIVATE")),
                Map.of("Cache-Control", List.of("no-store")),
                Map.of("Cache-Control", List.of("no-cache")),
                Map.of("Cache-Control", List.of("must-revalidate, max-age=10")),
                Map.of("Vary", List.of("*")),
                Map.of("Vary", List.of("x-unknown")),
                Map.of("Content-Type", List.of("text/event-stream")),
                Map.of("Content-Disposition", List.of("attachment")))) {
            assertFalse(cache.store(request(Map.of()), policy, response(headers)), headers.toString());
        }
    }

    @Test
    void shouldIsolateEveryOriginalHeaderAndPolicyChange() {
        CacheRequest en = request(Map.of("Accept-Language", List.of("en")));
        CacheRequest zh = request(Map.of("accept-language", List.of("zh")));
        assertTrue(cache.store(en, policy, response(Map.of("Vary", List.of("Accept-Language")))));
        assertTrue(cache.lookup(en, policy).isPresent());
        assertTrue(cache.lookup(zh, policy).isEmpty());
        assertTrue(cache.lookup(request(Map.of("Accept-Language", List.of("en"), "X-Custom", List.of("x"))), policy).isEmpty());
        CachePolicy changed = new CachePolicy(Duration.ofSeconds(9), Duration.ofSeconds(5), 1024, Set.of("accept-language"));
        assertTrue(cache.lookup(en, changed).isEmpty());
        CacheRequest otherRoute = new CacheRequest("other", "endpoint", "app", "client", "GET", "/items", "q=1",
                en.headers(), true, false);
        assertTrue(cache.lookup(otherRoute, policy).isEmpty());
    }

    @Test
    void shouldHonorOriginAgeAndFreshnessLimit() {
        CacheRequest request = request(Map.of());
        assertTrue(cache.store(request, policy, response(Map.of("Age", List.of("3"), "Cache-Control", List.of("max-age=5")))));
        assertEquals(List.of("3"), cache.lookup(request, policy).orElseThrow().headers().get("Age"));
        clock.advance(2);
        assertTrue(cache.lookup(request, policy).isEmpty());
        assertFalse(cache.store(request, policy, response(Map.of("Age", List.of("5"), "Cache-Control", List.of("max-age=5")))));
    }

    @Test
    void shouldDefensivelyCopyBodyAndDropUnsafeReplayHeaders() {
        byte[] bytes = {1, 2};
        CachedRepresentation response = new CachedRepresentation(200,
                Map.of("Content-Type", List.of("application/json"), "X-User-Id", List.of("never-replay")), bytes);
        bytes[0] = 9;
        assertTrue(cache.store(request(Map.of()), policy, response));
        CachedRepresentation first = cache.lookup(request(Map.of()), policy).orElseThrow();
        assertEquals(1, first.body()[0]);
        first.body()[0] = 8;
        assertEquals(1, cache.lookup(request(Map.of()), policy).orElseThrow().body()[0]);
        assertFalse(first.headers().containsKey("X-User-Id"));
    }

    @Test
    void shouldRejectOversizedRepresentationsAndInvalidatePriorEntryOnNonCacheableUpdate() {
        CacheRequest request = request(Map.of());
        assertTrue(cache.store(request, policy, response(Map.of())));
        assertFalse(cache.store(request, policy, new CachedRepresentation(200,
                Map.of("Content-Type", List.of("application/json")), new byte[1025])));
        assertTrue(cache.lookup(request, policy).isEmpty());
    }

    @Test
    void shouldNotStoreErrorOrPartialResponses() {
        for (int status : List.of(206, 302, 401, 403, 429, 500, 503)) {
            assertFalse(cache.store(request(Map.of()), policy,
                    new CachedRepresentation(status, Map.of("Content-Type", List.of("application/json")), new byte[1])));
        }
    }

    @Test
    void shouldRespectTotalWeightAndSharedRevalidationRestrictions() {
        CaffeineResponseCache smallCache = new CaffeineResponseCache(1024, clock);
        assertFalse(smallCache.store(request(Map.of()), policy, new CachedRepresentation(200,
                Map.of("Content-Type", List.of("application/json")), new byte[1024])));
        assertTrue(cache.store(request(Map.of()), policy, response(Map.of("Cache-Control", List.of("s-maxage=2")))));
        clock.advance(2);
        assertTrue(cache.fallback(request(Map.of()), policy, Fallback.STALE_CACHE, UpstreamFailure.TIMEOUT).isEmpty());
    }

    @Test
    void shouldBoundCombinedEntriesAndAccountForOriginDate() {
        CaffeineResponseCache smallCache = new CaffeineResponseCache(1024, clock);
        java.util.ArrayList<CacheRequest> requests = new java.util.ArrayList<CacheRequest>();
        for (int index = 0; index < 20; index++) {
            CacheRequest request = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items",
                    Integer.toString(index), Map.of(), true, false);
            requests.add(request);
            smallCache.store(request, policy, response(Map.of()));
        }
        assertTrue(requests.stream().filter(request -> smallCache.lookup(request, policy).isPresent()).count() <= 1);
        assertTrue(cache.store(request(Map.of()), policy,
                response(Map.of("Date", List.of("Mon, 07 Sep 2026 23:59:53 GMT")))));
        assertEquals(List.of("7"), cache.lookup(request(Map.of()), policy).orElseThrow().headers().get("Age"));
        clock.advance(3);
        assertTrue(cache.lookup(request(Map.of()), policy).isEmpty());
    }

    @Test
    void shouldAddCollectionDelayToOriginAgeWithoutExtendingFreshness() {
        assertTrue(cache.store(request(Map.of()), policy, response(Map.of("Age", List.of("3"))), Duration.ofSeconds(4)));
        assertEquals(List.of("7"), cache.lookup(request(Map.of()), policy).orElseThrow().headers().get("Age"));
        clock.advance(3);
        assertTrue(cache.lookup(request(Map.of()), policy).isEmpty());
    }

    @Test
    void shouldIsolateControlledForwardedHeadersAndRejectSubjectEvenWithoutOriginalCredentials() {
        CacheRequest first = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of(), true, false, Map.of("X-Variant", List.of("one")), false);
        CacheRequest changed = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of(), true, false, Map.of("X-Variant", List.of("two")), false);
        assertTrue(cache.store(first, policy, response(Map.of())));
        assertTrue(cache.lookup(first, policy).isPresent());
        assertTrue(cache.lookup(changed, policy).isEmpty());
        CacheRequest impersonated = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of("X-Variant", List.of("one")), true, false, Map.of(), false);
        assertTrue(cache.lookup(impersonated, policy).isEmpty());
        CacheRequest subject = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of(), true, false, first.forwardedHeaders(), true);
        assertTrue(cache.lookup(subject, policy).isEmpty());
        assertFalse(cache.store(subject, policy, response(Map.of())));
        CacheRequest strippedCredential = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of("Authorization", List.of("Bearer original")), true, false, Map.of(), false);
        assertFalse(cache.store(strippedCredential, policy, response(Map.of())));
    }

    @Test
    void shouldCacheFullyCollectedChunkedJsonWithoutReplayingTransferEncoding() {
        assertTrue(cache.store(request(Map.of()), policy, response(Map.of("Transfer-Encoding", List.of("chunked")))));
        CachedRepresentation cached = cache.lookup(request(Map.of()), policy).orElseThrow();
        assertFalse(cached.headers().containsKey("Transfer-Encoding"));
        assertEquals(2, cached.bodySize());
    }

    private static CacheRequest request(Map<String, List<String>> headers) {
        return new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1", headers, true, false);
    }

    @Test
    void shouldRejectUnframedBodyWithoutConflatingItWithStreamingEndpoint() {
        CacheRequest request = new CacheRequest("route", "endpoint", "app", "client", "GET", "/items", "q=1",
                Map.of(), true, false, Map.of(), false, true);
        assertFalse(cache.requestFacts(request).streaming());
        assertTrue(cache.requestFacts(request).bodyPresent());
        assertFalse(cache.requestFacts(request).bodyFramingPresent());
        assertFalse(cache.store(request, policy, response(Map.of())));
        assertTrue(cache.lookup(request, policy).isEmpty());
        assertTrue(cache.fallback(request, policy, Fallback.STALE_CACHE, UpstreamFailure.TIMEOUT).isEmpty());
    }

    @Test
    void shouldInvalidateOldEntryForCookiesRecordedOutsideHeaderMap() {
        CacheRequest request = request(Map.of());
        CachedRepresentation representation = response(Map.of());
        assertTrue(cache.store(request, policy, representation));
        assertFalse(cache.store(request, policy, new CachedRepresentation(representation.status(),
                representation.headers(), representation.body(), true)));
        assertTrue(cache.lookup(request, policy).isEmpty());
        assertTrue(cache.fallback(request, policy, Fallback.STALE_CACHE, UpstreamFailure.SERVER_ERROR).isEmpty());
    }

    @Test
    void shouldParseControlHeadersCaseInsensitivelyIntoFacts() {
        assertTrue(cache.requestFacts(request(Map.of("aUtHoRiZaTiOn", List.of("value")))).credentialsPresent());
        for (String name : List.of("Range", "If-Range", "Transfer-Encoding", "Content-Length", "If-Match",
                "If-None-Match", "If-Modified-Since", "If-Unmodified-Since", "Cache-Control", "Pragma")) {
            com.cloud.gateway.domain.policy.capability.CacheRequestFacts facts =
                    cache.requestFacts(request(Map.of(name, List.of("value"))));
            assertEquals(name.equals("Range"), facts.rangeRequested(), name);
            assertEquals(name.startsWith("If-"), facts.conditional(), name);
            assertEquals(name.equals("Cache-Control") || name.equals("Pragma"), facts.cacheDirectivesPresent(), name);
            assertEquals(name.equals("Transfer-Encoding") || name.equals("Content-Length"), facts.bodyFramingPresent(), name);
            assertFalse(facts.bodyPresent(), name);
        }
        assertEquals(cache.requestFacts(request(Map.of())),
                cache.requestFacts(request(Map.of("Accept-Language", List.of("en")))));
    }

    @Test
    void shouldPreserveConservativeAdmissionForZeroLengthAndPublicRequestDirective() {
        for (Map<String, List<String>> headers : List.of(
                Map.of("Content-Length", List.of("0")), Map.of("Cache-Control", List.of("public")))) {
            CacheRequest request = request(headers);
            assertFalse(cache.store(request, policy, response(Map.of())));
            assertTrue(cache.lookup(request, policy).isEmpty());
        }
    }

    @Test
    void shouldParseVaryLinesAndCaseBeforeDomainPolicyComparison() {
        assertTrue(cache.store(request(Map.of()), policy,
                response(Map.of("Vary", List.of("Accept-Language", "accept-language")))));
        assertFalse(cache.store(request(Map.of()), policy,
                response(Map.of("Vary", List.of("Accept-Language, unknown")))));
        assertTrue(cache.lookup(request(Map.of()), policy).isEmpty());
        assertFalse(cache.store(request(Map.of()), policy,
                response(Map.of("Vary", List.of("Accept-Language, *")))));
    }

    private static CachedRepresentation response(Map<String, List<String>> extra) {
        HashMap<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put("Content-Type", List.of("application/json"));
        headers.putAll(extra);
        return new CachedRepresentation(200, headers, new byte[]{1, 2});
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-08T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
