package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotEndpointResolverTest {
    private final SnapshotEndpointResolver resolver = new SnapshotEndpointResolver();

    @Test
    void shouldResolveTheOnlyMatchingEndpoint() {
        EndpointRegistry catalog = new EndpointRegistry(List.of(
                endpoint("ORDER.GET", "GET", "/api/orders/{orderId}"),
                endpoint("ORDER.CREATE", "POST", "/api/orders")));

        Endpoint resolved = resolver.resolve(catalog, "GET", "/api/orders/42").orElseThrow();

        assertEquals("ORDER.GET", resolved.endpointId().value());
    }

    @Test
    void shouldFailClosedWhenPatternsFromDifferentEndpointsOverlap() {
        EndpointRegistry catalog = new EndpointRegistry(List.of(
                endpoint("API.ALL", "GET", "/api/**"),
                endpoint("ORDER.ALL", "GET", "/api/orders/**")));

        assertThrows(DomainException.class,
                () -> resolver.resolve(catalog, "GET", "/api/orders/42"));
    }

    private static Endpoint endpoint(String endpointId, String method, String pattern) {
        return new Endpoint(new EndpointId(endpointId), "order",
                List.of(new EndpointMatcher(method, pattern)), "order");
    }
}
