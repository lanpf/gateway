package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndpointRegistrySelectionTest {

    @Test
    void shouldLookupEndpointsById() {
        Endpoint one = endpoint("one", "/a/**");
        EndpointRegistry registry = new EndpointRegistry(List.of(one));

        assertSame(one, registry.find(one.getId()).orElseThrow());
        assertTrue(registry.find(new EndpointId("missing")).isEmpty());
    }

    @Test
    void shouldRejectDuplicateMatchersAcrossEndpoints() {
        Endpoint one = endpoint("one", "/a/**");
        Endpoint two = endpoint("two", "/a/**");

        assertThrows(DomainException.class, () -> new EndpointRegistry(List.of(one, two)));
    }

    @Test
    void shouldRejectDuplicateEndpointIds() {
        assertThrows(DomainException.class,
                () -> new EndpointRegistry(List.of(endpoint("one", "/a"), endpoint("one", "/b"))));
    }

    @Test
    void shouldRejectNullElementsAndMissingMatchersWithDomainErrors() {
        assertThrows(DomainException.class, () -> new EndpointRegistry(Arrays.asList((Endpoint) null)));
        assertThrows(DomainException.class, () -> new Endpoint(new EndpointId("one"), new ServiceId("service"),
                new RouteId("route"), Arrays.asList((EndpointMatcher) null), Set.of(), EndpointStatus.ENABLED,
                policy()));
        assertThrows(DomainException.class, () -> new Endpoint(new EndpointId("one"), new ServiceId("service"),
                new RouteId("route"), List.of(), Set.of(), EndpointStatus.ENABLED, policy()));
    }

    private static Endpoint endpoint(String id, String path) {
        return new Endpoint(new EndpointId(id), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", path)), Set.of(), EndpointStatus.ENABLED, policy());
    }

    private static EndpointAccess policy() {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of());
    }
}
