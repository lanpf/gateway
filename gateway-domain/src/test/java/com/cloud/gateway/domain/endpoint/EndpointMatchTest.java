package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.error.FrameworkException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointMatchTest {
    private static final EndpointId ID = new EndpointId("ONE");

    @Test
    void shouldClassifyZeroOneAndDistinctCandidates() {
        assertInstanceOf(EndpointMatch.None.class, EndpointMatch.of(List.of()));

        Endpoint only = endpoint("ONE", "/a");
        EndpointMatch.Unique unique = assertInstanceOf(EndpointMatch.Unique.class, EndpointMatch.of(List.of(only)));
        assertEquals(ID, unique.endpoint().getId());

        assertInstanceOf(EndpointMatch.Ambiguous.class,
                EndpointMatch.of(List.of(endpoint("ONE", "/a"), endpoint("TWO", "/b"))));
    }

    @Test
    void shouldCollapseIdenticalCandidatesWhenOneEndpointMatchesSeveralOfItsMatchers() {
        Endpoint first = endpoint("ONE", "/a/**");

        EndpointMatch.Unique unique = assertInstanceOf(EndpointMatch.Unique.class,
                EndpointMatch.of(Arrays.asList(first, first)));
        assertEquals(ID, unique.endpoint().getId());
    }

    @Test
    void shouldTreatSameIdWithDivergentContentAsAmbiguous() {
        Endpoint first = endpoint("ONE", "/a/**");
        Endpoint divergent = new Endpoint(ID, first.getServiceId(), first.getRouteId(),
                List.of(new EndpointMatcher("GET", "/a/b")), first.getTags(), EndpointStatus.ENABLED, first.getAccess());

        assertInstanceOf(EndpointMatch.Ambiguous.class, EndpointMatch.of(Arrays.asList(first, divergent)));
    }

    @Test
    void shouldRejectNullCandidates() {
        assertThrows(FrameworkException.class, () -> EndpointMatch.of(null));
        assertThrows(FrameworkException.class, () -> EndpointMatch.of(Collections.singletonList(null)));
    }

    private static Endpoint endpoint(String id, String path) {
        return new Endpoint(new EndpointId(id), new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", path)), java.util.Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, java.util.Set.of(), java.util.Set.of()));
    }
}
