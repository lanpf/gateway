package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The budget reaches Netty only through the route metadata, and the key names and value types are
 * Spring Cloud Gateway's contract rather than ours, so they are pinned here.
 */
class RouteTimeoutMetadataTest {
    private static final TimeoutConfig POLICY = new TimeoutConfig(
            Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(5));

    @Test
    void shouldPublishConnectAndResponseBudgetsInMilliseconds() {
        MockServerWebExchange exchange = exchangeWith(route(Map.of()));

        RouteTimeoutMetadata.apply(exchange, POLICY);

        Map<String, Object> metadata = configuredRoute(exchange).getMetadata();
        assertEquals(1000, metadata.get(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR));
        assertEquals(3000L, metadata.get(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR));
    }

    /**
     * The client reads the connect value through a number coercion, so publishing a type it cannot
     * coerce would fail at request time rather than here.
     */
    @Test
    void shouldPublishTheConnectBudgetAsANumber() {
        MockServerWebExchange exchange = exchangeWith(route(Map.of()));

        RouteTimeoutMetadata.apply(exchange, POLICY);

        assertTrue(configuredRoute(exchange).getMetadata().get(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR)
                instanceof Integer);
    }

    /** Rebuilding the route must not drop the deployment's own metadata. */
    @Test
    void shouldKeepTheRoutesOtherMetadataAndIdentity() {
        MockServerWebExchange exchange = exchangeWith(route(Map.of("keep", "me", "other", 7)));

        RouteTimeoutMetadata.apply(exchange, POLICY);

        Route configured = configuredRoute(exchange);
        assertEquals("me", configured.getMetadata().get("keep"));
        assertEquals(7, configured.getMetadata().get("other"));
        assertEquals("orders", configured.getId());
        assertEquals(URI.create("http://localhost:9000"), configured.getUri());
    }

    /** Routes are matched later, so a request without one must pass through untouched. */
    @Test
    void shouldDoNothingWhenNoRouteIsBound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/read"));

        RouteTimeoutMetadata.apply(exchange, POLICY);

        assertNull(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR));
    }

    private static MockServerWebExchange exchangeWith(Route route) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/read"));
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    private static Route configuredRoute(MockServerWebExchange exchange) {
        return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    }

    private static Route route(Map<String, Object> metadata) {
        return Route.async()
                .id("orders")
                .uri(URI.create("http://localhost:9000"))
                .order(0)
                .asyncPredicate(exchange -> Mono.just(true))
                .metadata(metadata)
                .build();
    }
}
