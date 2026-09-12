package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.governance.*;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.capability.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class DownstreamPolicyHandlerTest {
    private final FakeCache cache = new FakeCache();
    private final FakeCircuits circuits = new FakeCircuits();
    private final DownstreamPolicyHandler handler = new DownstreamPolicyHandler(cache, circuits);
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("READ"), "catalog",
            List.of(new EndpointMatcher("GET", "/read")), "catalog-api");
    private static final IngressAccessGrantedContext CONTEXT = new IngressAccessGrantedContext(
            new IngressAccessGrantedContext.AppContext(new AppId("app"), "WEB"), null, null);
    private static final CachePolicy CACHE = new CachePolicy(Duration.ofSeconds(10), Duration.ofSeconds(30), 64, java.util.Set.of());
    private static final CircuitBreakerPolicy BREAKER = new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1),
            2, 10, 1, Duration.ofSeconds(1), CircuitBreakerPolicy.Fallback.STALE_CACHE);

    @Test void shouldServeFreshWithoutDownstreamAdmission() {
        MockServerWebExchange exchange = exchange();
        cache.fresh = Optional.of(representation("fresh"));
        handler.handle(exchange, exchange, ignored -> Mono.error(new AssertionError("no upstream")),
                ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals("fresh", exchange.getResponse().getBodyAsString().block());
        assertEquals(0, circuits.acquired);
        assertTrue(cache.observed.authorized());
    }

    @Test void shouldCountServerFailureAndServeStaleBeforeCommit() {
        MockServerWebExchange exchange = exchange();
        cache.stale = Optional.of(representation("stale"));
        handler.handle(exchange, exchange, target -> {
            target.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return target.getResponse().writeWith(Mono.just(target.getResponse().bufferFactory().wrap(new byte[]{1})));
        }, ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals("stale", exchange.getResponse().getBodyAsString().block());
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE, circuits.outcome.get());
        assertEquals(0, cache.stores);
    }

    @Test void shouldApplyTotalTimeoutAndTransportMetadata() {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id("catalog-api").uri("http://localhost:8080").predicate(ignored -> true).build());
        TimeoutPolicy timeout = new TimeoutPolicy(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(10));
        handler.handle(exchange, exchange, ignored -> Mono.never(), ENDPOINT, CONTEXT, policy(timeout)).block();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        assertEquals(100, route.getMetadata().get(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR));
        assertEquals(200L, route.getMetadata().get(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR));
        assertEquals(DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE, circuits.outcome.get());
    }

    @Test void shouldNotFallbackForLocalProgrammingFailure() {
        MockServerWebExchange exchange = exchange();
        cache.stale = Optional.of(representation("must-not-use"));
        assertThrows(IllegalStateException.class, () -> handler.handle(exchange, exchange,
                ignored -> Mono.error(new IllegalStateException("local")), ENDPOINT, CONTEXT, policy(null)).block());
        assertEquals(0, cache.fallbacks);
        assertEquals(DownstreamCircuitBreaker.Outcome.IGNORED, circuits.outcome.get());
    }

    @Test void shouldPassThroughOversizeBodyWithoutCachingOrFailing() {
        MockServerWebExchange exchange = exchange();
        byte[] body = new byte[100];
        handler.handle(exchange, exchange, target -> target.getResponse().writeWith(
                Mono.just(target.getResponse().bufferFactory().wrap(body))), ENDPOINT, CONTEXT, policy(null)).block();
        assertEquals(100, exchange.getResponse().getBodyAsString().block().length());
        assertEquals(0, cache.stores);
    }

    private static EffectivePolicy policy(TimeoutPolicy timeout) {
        return new EffectivePolicy(List.of(), timeout, BREAKER, CACHE, null);
    }
    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/read"));
    }
    private static CachedRepresentation representation(String body) {
        return new CachedRepresentation(200, Map.of("Content-Type", List.of("text/plain")),
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static final class FakeCache implements ResponseCache {
        @Override
        public com.cloud.gateway.domain.policy.capability.CacheRequestFacts requestFacts(CacheRequest request) {
            return new com.cloud.gateway.domain.policy.capability.CacheRequestFacts(request.method(),
                    request.authorized(), request.streaming(), request.subjectPresent(),
                    request.headers().containsKey("authorization") || request.headers().containsKey("cookie"),
                    request.bodyPresent(), false, false, false, false);
        }
        private Optional<CachedRepresentation> fresh = Optional.empty(), stale = Optional.empty();
        private CacheRequest observed;
        private int stores, fallbacks;
        public Optional<CachedRepresentation> lookup(CacheRequest request, CachePolicy policy) {
            observed = request; return fresh;
        }
        public boolean store(CacheRequest request, CachePolicy policy, CachedRepresentation representation, Duration delay) {
            stores++; return true;
        }
        public Optional<CachedRepresentation> fallback(CacheRequest request, CachePolicy policy,
                CircuitBreakerPolicy.Fallback action, UpstreamFailure failure) {
            fallbacks++; return stale;
        }
    }
    private static final class FakeCircuits implements DownstreamCircuitBreaker {
        private int acquired;
        private final AtomicReference<Outcome> outcome = new AtomicReference<>();
        public Permit acquire(String routeId, CircuitBreakerPolicy policy) {
            acquired++;
            return new Permit() {
                public boolean allowed() { return true; }
                public void complete(Outcome completion) { outcome.compareAndSet(null, completion); }
            };
        }
    }
}
