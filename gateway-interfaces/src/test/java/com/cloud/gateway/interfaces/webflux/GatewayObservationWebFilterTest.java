package com.cloud.gateway.interfaces.webflux;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayObservationWebFilterTest {
    @Test
    void shouldObserveEarlyRejectionWithoutRequestIdentityTags() {
        var registry = new SimpleMeterRegistry();
        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/unknown?secret=hidden"));
            new GatewayObservationWebFilter(registry).filter(exchange, request -> {
                request.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return request.getResponse().setComplete();
            }).block();
            Timer timer = registry.get("gateway.ingress.requests").tags("outcome", "completed", "status_class", "4xx").timer();
            assertEquals(1, timer.count());
            assertEquals(2, timer.getId().getTags().size());
        } finally {
            registry.close();
        }
    }

    @Test
    void shouldObserveFailureWithoutRecordingExceptionMessage() {
        var registry = new SimpleMeterRegistry();
        try {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
            assertThrows(IllegalStateException.class, () -> new GatewayObservationWebFilter(registry)
                    .filter(exchange, request -> Mono.error(new IllegalStateException("sensitive message"))).block());
            assertEquals(1, registry.get("gateway.ingress.requests")
                    .tags("outcome", "error", "status_class", "5xx").timer().count());
        } finally {
            registry.close();
        }
    }

    @Test
    void shouldWorkWithoutMetricsRegistry() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        new GatewayObservationWebFilter(null).filter(exchange, request -> request.getResponse().setComplete()).block();
        assertEquals(true, exchange.getResponse().isCommitted());
    }
}
