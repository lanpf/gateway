package com.cloud.gateway.interfaces.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotEndpointResolverTest {
    private final SnapshotEndpointResolver resolver = new SnapshotEndpointResolver();

    @Test
    void shouldResolveTheOnlyMatchingEndpoint() {
        EndpointCatalog catalog = new EndpointCatalog(List.of(
                endpoint("ORDER.GET", "GET", "/api/orders/{orderId}"),
                endpoint("ORDER.CREATE", "POST", "/api/orders")));

        EndpointDefinition resolved = resolver.resolve(catalog, "GET", "/api/orders/42").orElseThrow();

        assertEquals("ORDER.GET", resolved.endpointId().value());
    }

    @Test
    void shouldFailClosedWhenPatternsFromDifferentEndpointsOverlap() {
        EndpointCatalog catalog = new EndpointCatalog(List.of(
                endpoint("API.ALL", "GET", "/api/**"),
                endpoint("ORDER.ALL", "GET", "/api/orders/**")));

        assertThrows(DomainException.class,
                () -> resolver.resolve(catalog, "GET", "/api/orders/42"));
    }

    private static EndpointDefinition endpoint(String endpointId, String method, String pattern) {
        return new EndpointDefinition(new EndpointId(endpointId), "order",
                List.of(new EndpointMatcher(method, pattern)), "order");
    }
}
