package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.governance.CacheRequest;
import com.cloud.gateway.application.governance.CachedRepresentation;
import com.cloud.gateway.application.governance.DownstreamCircuitBreaker;
import com.cloud.gateway.application.governance.ResponseCache;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.capability.CachePolicy;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import com.cloud.gateway.domain.policy.capability.TimeoutPolicy;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class DownstreamPolicyHandlerLifecycleTest {
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("READ"), "catalog",
            List.of(new EndpointMatcher("GET", "/read")), "catalog-api");
    private static final IngressAccessGrantedContext CONTEXT = new IngressAccessGrantedContext(
            new IngressAccessGrantedContext.AppContext(new AppId("app"), "WEB"), null, null);
    private static final CachePolicy CACHE = new CachePolicy(Duration.ofSeconds(10), Duration.ofSeconds(20), 128, Set.of());
    private static final CircuitBreakerPolicy CIRCUIT = new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
            2, 10, 1, Duration.ofSeconds(1), CircuitBreakerPolicy.Fallback.STALE_CACHE);
    private final RecordingCache cache = new RecordingCache();
    private final RecordingCircuits circuits = new RecordingCircuits();
    private final DownstreamPolicyHandler handler = new DownstreamPolicyHandler(cache, circuits);

    @Test
    void shouldCancelEndlessErrorBodyAndServeStaleWithoutWaiting() {
        MockServerWebExchange exchange = exchange();
        cache.stale = Optional.of(representation());
        AtomicBoolean cancelled = new AtomicBoolean();
        handler.handle(exchange, exchange, target -> {
            target.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return target.getResponse().writeWith(Flux.<DataBuffer>never().doOnCancel(() -> cancelled.set(true)));
        }, ENDPOINT, CONTEXT, policy(null)).block(Duration.ofSeconds(1));
        assertTrue(cancelled.get());
        assertEquals("stale", exchange.getResponse().getBodyAsString().block());
        assertEquals(DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE, circuits.outcome.get());
    }

    @Test
    void shouldFallbackForEmptyAndFlushingErrors() {
        for (boolean flush : List.of(false, true)) {
            MockServerWebExchange exchange = exchange();
            cache.stale = Optional.of(representation());
            handler.handle(exchange, exchange, target -> {
                target.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return flush ? target.getResponse().writeAndFlushWith(Flux.never()) : target.getResponse().setComplete();
            }, ENDPOINT, CONTEXT, policy(null)).block(Duration.ofSeconds(1));
            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertEquals("stale", exchange.getResponse().getBodyAsString().block());
        }
    }

    @Test
    void shouldRemoveOriginHeadersAndCookiesButKeepGatewayHeadersOnSyntheticError() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().getHeaders().set("X-Content-Type-Options", "nosniff");
        handler.handle(exchange, exchange, target -> {
            target.getResponse().getHeaders().set(HttpHeaders.SET_COOKIE, "sid=origin");
            target.getResponse().getHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
            target.getResponse().getHeaders().set(HttpHeaders.LOCATION, "/origin");
            target.getResponse().addCookie(ResponseCookie.from("late", "origin").build());
            target.getResponse().beforeCommit(() -> {
                target.getResponse().getHeaders().add(HttpHeaders.SET_COOKIE, "commit=origin");
                return Mono.empty();
            });
            return Mono.error(new java.util.concurrent.TimeoutException());
        }, ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        assertEquals("nosniff", exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
        assertFalse(exchange.getResponse().getHeaders().containsKey(HttpHeaders.SET_COOKIE));
        assertFalse(exchange.getResponse().getHeaders().containsKey(HttpHeaders.CONTENT_ENCODING));
        assertFalse(exchange.getResponse().getHeaders().containsKey(HttpHeaders.LOCATION));
        assertTrue(exchange.getResponse().getCookies().isEmpty());
    }

    @Test
    void shouldPreserveGatewayCorsAndSecurityHeadersOnHitAndStaleFallback() {
        for (boolean fresh : List.of(false, true)) {
            MockServerWebExchange exchange = exchange();
            exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example");
            exchange.getResponse().getHeaders().set("X-Content-Type-Options", "nosniff");
            cache.fresh = fresh ? Optional.of(representation()) : Optional.empty();
            cache.stale = Optional.of(representation());
            handler.handle(exchange, exchange, target -> {
                target.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                target.getResponse().getHeaders().set(HttpHeaders.SET_COOKIE, "sid=origin");
                return target.getResponse().setComplete();
            }, ENDPOINT, CONTEXT, policy(null)).block();
            assertEquals("https://app.example", exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            assertEquals("nosniff", exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
            assertFalse(exchange.getResponse().getHeaders().containsKey(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    void shouldUseCommittedMetadataForCacheAndNeverStoreCommittedCookies() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, exchange, target -> {
            target.getResponse().beforeCommit(() -> {
                target.getResponse().getHeaders().setCacheControl("private");
                return Mono.empty();
            });
            return target.getResponse().writeWith(Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1})));
        }, ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals(List.of("private"), cache.stored.headers().get(HttpHeaders.CACHE_CONTROL));
        cache.stored = null;
        MockServerWebExchange cookieExchange = exchange();
        handler.handle(cookieExchange, cookieExchange, target -> {
            target.getResponse().beforeCommit(() -> {
                target.getResponse().addCookie(ResponseCookie.from("sid", "private").build());
                return Mono.empty();
            });
            return target.getResponse().writeWith(Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1})));
        }, ENDPOINT, CONTEXT, policy(null)).block();
        assertNull(cache.stored);
    }

    @Test
    void shouldNotPoisonCircuitOrFallbackForBodyFailureAfterHeaders() {
        MockServerWebExchange exchange = exchange();
        cache.stale = Optional.of(representation());
        assertThrows(Exception.class, () -> handler.handle(exchange, exchange, target ->
                target.getResponse().writeWith(Flux.error(new IOException("body interrupted"))),
                ENDPOINT, CONTEXT, policy(null)).block());
        assertEquals(DownstreamCircuitBreaker.Outcome.SUCCESS, circuits.outcome.get());
        assertEquals(0, cache.fallbacks);
        assertNull(cache.stored);
    }

    @Test
    void shouldEndCircuitObservationAtHeadersNotClientWriteCompletion() {
        MockServerWebExchange exchange = exchange();
        TimeoutPolicy timeout = new TimeoutPolicy(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(50));
        cache.stale = Optional.of(representation());
        handler.handle(exchange, exchange, target -> target.getResponse().writeWith(Flux.never()),
                ENDPOINT, CONTEXT, policy(timeout)).block(Duration.ofSeconds(1));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        assertEquals(DownstreamCircuitBreaker.Outcome.SUCCESS, circuits.outcome.get());
        assertEquals(0, cache.fallbacks);
    }

    @Test
    void shouldRecheckStaleEligibilityAfterCancellation() {
        MockServerWebExchange exchange = exchange();
        cache.stale = Optional.of(representation());
        handler.handle(exchange, exchange, target -> {
            target.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return target.getResponse().writeWith(Flux.<DataBuffer>never()
                    .doOnCancel(() -> cache.stale = Optional.empty()));
        }, ENDPOINT, CONTEXT, policy(null)).block(Duration.ofSeconds(1));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertNotEquals("stale", exchange.getResponse().getBodyAsString().block());
    }

    @Test
    void shouldReleaseAlreadyAllocatedErrorBufferWhenCancellingOrigin() {
        MockServerWebExchange exchange = exchange();
        cache.stale = Optional.of(representation());
        org.springframework.core.io.buffer.NettyDataBufferFactory factory = new org.springframework.core.io.buffer.NettyDataBufferFactory(io.netty.buffer.PooledByteBufAllocator.DEFAULT);
        org.springframework.core.io.buffer.NettyDataBuffer buffer = factory.allocateBuffer(16);
        buffer.write(new byte[]{1});
        try {
            handler.handle(exchange, exchange, target -> {
                target.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return target.getResponse().writeWith(Mono.just(buffer));
            }, ENDPOINT, CONTEXT, policy(null)).block();
            assertEquals(0, buffer.getNativeBuffer().refCnt());
        } finally {
            if (buffer.getNativeBuffer().refCnt() > 0) {
                buffer.release();
            }
        }
    }

    @Test
    void shouldNotFallbackOrChangeCircuitOutcomeWhenCommittedClientWriteTimesOut() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setWriteHandler(body -> body
                .doOnNext(org.springframework.core.io.buffer.DataBufferUtils::release).then(Mono.never()));
        cache.stale = Optional.of(representation());
        TimeoutPolicy timeout = new TimeoutPolicy(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(50));
        assertThrows(Exception.class, () -> handler.handle(exchange, exchange,
                target -> target.getResponse().writeWith(Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1}))),
                ENDPOINT, CONTEXT, policy(timeout)).block(Duration.ofSeconds(1)));
        assertTrue(exchange.getResponse().isCommitted());
        assertEquals(DownstreamCircuitBreaker.Outcome.SUCCESS, circuits.outcome.get());
        assertEquals(0, cache.fallbacks);
        assertNull(cache.stored);
    }

    @Test
    void shouldPassControlledForwardedHeadersSeparatelyAndSkipSubjectCaching() {
        MockServerWebExchange exchange = exchange();
        org.springframework.web.server.ServerWebExchange forwarded = exchange.mutate().request(request -> request.header("X-Variant", "controlled")).build();
        cache.fresh = Optional.of(representation());
        handler.handle(exchange, forwarded, target -> Mono.error(new AssertionError("cache hit expected")),
                ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals(List.of("controlled"), cache.observed.forwardedHeaders().get("x-variant"));
        assertFalse(cache.observed.headers().containsKey("x-variant"));
        IngressAccessGrantedContext subject = new IngressAccessGrantedContext(CONTEXT.appContext(), null,
                new IngressAccessGrantedContext.SubjectContext(SubjectType.HOST_SESSION,
                        "user", "session", Set.of(), Set.of()));
        MockServerWebExchange subjectExchange = exchange();
        cache.observed = null;
        handler.handle(subjectExchange, subjectExchange, target -> target.getResponse().writeWith(
                Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1}))),
                ENDPOINT, subject, policy(null)).block();
        assertNull(cache.observed);
        assertNull(cache.stored);
    }

    @Test
    void shouldCompletePermitExactlyOnceWhenClientCancelsBeforeHeaders() {
        MockServerWebExchange exchange = exchange();
        reactor.core.Disposable subscription = handler.handle(exchange, exchange, target -> Mono.never(),
                ENDPOINT, CONTEXT, policy(null)).subscribe();
        subscription.dispose();
        subscription.dispose();
        assertEquals(1, circuits.completions.get());
        assertEquals(DownstreamCircuitBreaker.Outcome.IGNORED, circuits.outcome.get());
        assertEquals(0, cache.fallbacks);
    }

    @Test
    void shouldSkipOriginAndPermitCompletionWhenCircuitIsOpen() {
        MockServerWebExchange exchange = exchange();
        circuits.allowed = false;
        cache.stale = Optional.of(representation());
        handler.handle(exchange, exchange, target -> Mono.error(new AssertionError("origin must not run")),
                ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals("stale", exchange.getResponse().getBodyAsString().block());
        assertEquals(ResponseCache.UpstreamFailure.CIRCUIT_OPEN, cache.failure);
        assertEquals(0, circuits.completions.get());
    }

    @Test
    void shouldCompletePermitExactlyOnceWhenClientCancelsAfterHeaders() {
        MockServerWebExchange exchange = exchange();
        reactor.core.Disposable subscription = handler.handle(exchange, exchange,
                target -> target.getResponse().writeWith(Flux.never()), ENDPOINT, CONTEXT, policy(null)).subscribe();
        subscription.dispose();
        assertEquals(1, circuits.completions.get());
        assertEquals(DownstreamCircuitBreaker.Outcome.SUCCESS, circuits.outcome.get());
        assertEquals(0, cache.fallbacks);
        assertNull(cache.stored);
    }

    @Test
    void shouldPreserveOriginFailureClassificationBeforeHeaders() {
        List<Throwable> failures = List.of(new IOException("connect"), new java.util.concurrent.TimeoutException(),
                new org.springframework.web.server.ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT),
                io.netty.handler.timeout.ReadTimeoutException.INSTANCE,
                reactor.netty.http.client.PrematureCloseException.TEST_EXCEPTION);
        for (Throwable failure : failures) {
            MockServerWebExchange exchange = exchange();
            cache.stale = Optional.of(representation());
            handler.handle(exchange, exchange, target -> Mono.error(failure), ENDPOINT, CONTEXT, policy(null)).block();
            assertEquals(failure instanceof IOException ? ResponseCache.UpstreamFailure.CONNECTION
                    : ResponseCache.UpstreamFailure.TIMEOUT, cache.failure);
            assertEquals("stale", exchange.getResponse().getBodyAsString().block());
        }
    }

    @Test
    void shouldPassRawRequestEvidenceAndFullCollectionDelayWithoutRenewingTtl() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.GET, java.net.URI.create("/read%2Fitem?a=2&a=1")));
        handler.handle(exchange, exchange, target -> target.getResponse().writeWith(
                Mono.delay(Duration.ofMillis(25)).map(ignored -> target.getResponse().bufferFactory().wrap(new byte[]{1}))),
                ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals("/read%2Fitem", cache.observed.path());
        assertEquals("a=2&a=1", cache.observed.query());
        assertEquals("catalog-api", cache.observed.routeId());
        assertTrue(cache.delay.compareTo(Duration.ofMillis(25)) >= 0);
        assertSame(CACHE, cache.storePolicy);
    }

    @Test
    void shouldNeverCaptureFlushingSuccessOrCredentialBearingRequests() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, exchange, target -> target.getResponse().writeAndFlushWith(
                Mono.just(Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1})))),
                ENDPOINT, CONTEXT, policy(null)).block();
        assertNull(cache.stored);
        for (String header : List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE)) {
            MockServerWebExchange credentialExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/read")
                    .header(header, "client"));
            handler.handle(credentialExchange, credentialExchange, target -> target.getResponse().writeWith(
                    Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1}))),
                    ENDPOINT, CONTEXT, policy(null)).block();
            assertNull(cache.stored);
        }
    }

    private static EffectivePolicy policy(TimeoutPolicy timeout) {
        return new EffectivePolicy(List.of(), timeout, CIRCUIT, CACHE, null);
    }

    @Test
    void shouldKeepManagedStreamingAndUnframedRequestBodyAsSeparateEvidence() {
        Endpoint streaming = new Endpoint(ENDPOINT.endpointId(), ENDPOINT.serviceId(),
                ENDPOINT.matchers(), ENDPOINT.routeId(), Set.of("streaming"));
        cache.fresh = Optional.of(representation());
        MockServerWebExchange streamExchange = exchange();
        handler.handle(streamExchange, streamExchange, target -> target.getResponse().setComplete(),
                streaming, CONTEXT, policy(null)).block();
        assertTrue(cache.inspected.streaming());
        assertFalse(cache.inspected.bodyPresent());
        assertNull(cache.observed);
        MockServerWebExchange bodyExchange = exchange();
        bodyExchange.getAttributes().put(GatewayRequestAttributes.REQUEST_BODY_PRESENT, true);
        handler.handle(bodyExchange, bodyExchange, target -> target.getResponse().setComplete(),
                ENDPOINT, CONTEXT, policy(null)).block();
        assertFalse(cache.inspected.streaming());
        assertTrue(cache.inspected.bodyPresent());
        assertNull(cache.observed);
    }
    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/read"));
    }
    private static CachedRepresentation representation() {
        return new CachedRepresentation(200, Map.of(HttpHeaders.CONTENT_TYPE, List.of("text/plain")),
                "stale".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static final class RecordingCache implements ResponseCache {
        private CacheRequest inspected;
        @Override
        public com.cloud.gateway.domain.policy.capability.CacheRequestFacts requestFacts(CacheRequest request) {
            inspected = request;
            return new com.cloud.gateway.domain.policy.capability.CacheRequestFacts(request.method(),
                    request.authorized(), request.streaming(), request.subjectPresent(),
                    request.headers().containsKey("authorization") || request.headers().containsKey("cookie"),
                    request.bodyPresent(), false, false, false, false);
        }
        private Optional<CachedRepresentation> fresh = Optional.empty();
        private Optional<CachedRepresentation> stale = Optional.empty();
        private CachedRepresentation stored;
        private CacheRequest observed;
        private int fallbacks;
        private Duration delay;
        private CachePolicy storePolicy;
        private UpstreamFailure failure;
        public Optional<CachedRepresentation> lookup(CacheRequest request, CachePolicy policy) {
            observed = request;
            return fresh;
        }
        public boolean store(CacheRequest request, CachePolicy policy, CachedRepresentation representation, Duration delay) {
            if (representation.cookiesPresent()) {
                return false;
            }
            stored = representation;
            this.delay = delay;
            this.storePolicy = policy;
            return true;
        }
        public Optional<CachedRepresentation> fallback(CacheRequest request, CachePolicy policy,
                CircuitBreakerPolicy.Fallback action, UpstreamFailure failure) {
            fallbacks++;
            this.failure = failure;
            return stale;
        }
    }
    private static final class RecordingCircuits implements DownstreamCircuitBreaker {
        private final AtomicReference<Outcome> outcome = new AtomicReference<>();
        private final AtomicInteger completions = new AtomicInteger();
        private boolean allowed = true;
        public Permit acquire(String routeId, CircuitBreakerPolicy policy) {
            return new Permit() {
                public boolean allowed() { return allowed; }
                public void complete(Outcome value) {
                    completions.incrementAndGet();
                    outcome.compareAndSet(null, value);
                }
            };
        }
    }
}
