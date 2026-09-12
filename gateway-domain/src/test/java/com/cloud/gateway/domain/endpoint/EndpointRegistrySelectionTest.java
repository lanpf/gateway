package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;
import java.util.List;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndpointRegistrySelectionTest {
    @Test
    void shouldRejectAmbiguousCandidatesWithoutDependingOnPathGrammar() {
        Endpoint one = endpoint("one", "/a/**");
        Endpoint two = endpoint("two", "/a/b");
        EndpointRegistry registry = new EndpointRegistry(List.of(one, two));
        assertTrue(registry.select(List.of()).isEmpty());
        assertSame(one, registry.select(List.of(one)).orElseThrow());
        assertThrows(DomainException.class, () -> registry.select(List.of(one, two)));
    }

    @Test
    void shouldRejectMissingDefinitionsAndMatchersWithDomainErrors() {
        assertThrows(DomainException.class, () -> new EndpointRegistry(Arrays.asList((Endpoint) null)));
        assertThrows(DomainException.class, () -> new EndpointRegistry(List.of())
                .select(Arrays.asList((Endpoint) null)));
        assertThrows(DomainException.class, () -> new Endpoint(new EndpointId("one"), "service",
                Arrays.asList((EndpointMatcher) null), "route"));
    }

    private static Endpoint endpoint(String id, String path) {
        return new Endpoint(new EndpointId(id), "service", List.of(new EndpointMatcher("GET", path)), "route");
    }
}
