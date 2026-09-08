package com.cloud.gateway.infrastructure.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalPolicySnapshotHasherTest {
    private static final String MIGRATION_COMPATIBLE_HASH =
            "029ce0d8c03227ff789ff546acf2a74a256993da0b5c3b2b2abb6ebe46a2972d";
    private static final String FIRST_CLIENT_APP_ID = "app-a";
    private static final String SECOND_CLIENT_APP_ID = "app-b";

    private final CanonicalPolicySnapshotHasher hasher =
            new CanonicalPolicySnapshotHasher(new CanonicalPolicyContentMapper(), new ObjectMapper());

    @Test
    void shouldProduceSameHashForEquivalentMapInsertionOrders() {
        Map<ClientAppId, IngressClientAppPolicy> forward = new LinkedHashMap<>();
        forward.put(new ClientAppId(FIRST_CLIENT_APP_ID), client(FIRST_CLIENT_APP_ID));
        forward.put(new ClientAppId(SECOND_CLIENT_APP_ID), client(SECOND_CLIENT_APP_ID));
        Map<ClientAppId, IngressClientAppPolicy> reverse = new LinkedHashMap<>();
        reverse.put(new ClientAppId(SECOND_CLIENT_APP_ID), client(SECOND_CLIENT_APP_ID));
        reverse.put(new ClientAppId(FIRST_CLIENT_APP_ID), client(FIRST_CLIENT_APP_ID));

        assertEquals(hasher.hash(content(forward)), hasher.hash(content(reverse)));
    }

    @Test
    void shouldProduceSameHashForEquivalentMatcherOrders() {
        List<EndpointMatcher> forward = List.of(
                new EndpointMatcher("GET", "/first"),
                new EndpointMatcher("POST", "/second"));
        List<EndpointMatcher> reverse = List.of(
                new EndpointMatcher("POST", "/second"),
                new EndpointMatcher("GET", "/first"));

        assertEquals(hasher.hash(content(Map.of(), forward)), hasher.hash(content(Map.of(), reverse)));
    }

    @Test
    void shouldPreserveHashAfterEntityIdMigration() {
        Map<ClientAppId, IngressClientAppPolicy> clientApps = new LinkedHashMap<>();
        clientApps.put(new ClientAppId(FIRST_CLIENT_APP_ID), client(FIRST_CLIENT_APP_ID));
        clientApps.put(new ClientAppId(SECOND_CLIENT_APP_ID), client(SECOND_CLIENT_APP_ID));

        assertEquals(MIGRATION_COMPATIBLE_HASH, hasher.hash(content(clientApps)));
    }

    @Test
    void shouldIncludeNormalizedBrowserOriginsInHashIndependentOfSetOrder() {
        IngressClientAppPolicy forward = client(FIRST_CLIENT_APP_ID,
                Set.of("https://one.example.com", "https://two.example.com"));
        IngressClientAppPolicy reverse = client(FIRST_CLIENT_APP_ID,
                new LinkedHashSet<>(List.of(
                        "https://two.example.com", "https://one.example.com")));
        IngressClientAppPolicy changed = client(FIRST_CLIENT_APP_ID,
                Set.of("https://three.example.com"));

        assertEquals(
                hasher.hash(content(Map.of(forward.clientAppId(), forward))),
                hasher.hash(content(Map.of(reverse.clientAppId(), reverse))));
        assertNotEquals(
                hasher.hash(content(Map.of(forward.clientAppId(), forward))),
                hasher.hash(content(Map.of(changed.clientAppId(), changed))));
    }

    private static IngressPolicyContent content(Map<ClientAppId, IngressClientAppPolicy> clientApps) {
        return content(clientApps, List.of(new EndpointMatcher("GET", "/test")));
    }

    private static IngressPolicyContent content(
            Map<ClientAppId, IngressClientAppPolicy> clientApps,
            List<EndpointMatcher> matchers
    ) {
        EndpointCatalog catalog = new EndpointCatalog(List.of(new EndpointDefinition(
                new EndpointId("TEST.ENDPOINT"), "test-service",
                matchers, "test-route")));
        return new IngressPolicyContent(catalog, clientApps, Map.of(), Map.of(), Map.of());
    }

    private static IngressClientAppPolicy client(String clientAppId) {
        return client(clientAppId, Set.of());
    }

    private static IngressClientAppPolicy client(String clientAppId, Set<String> allowedBrowserOrigins) {
        return new IngressClientAppPolicy(
                new ClientAppId(clientAppId), ClientAppStatus.ENABLED, "TEST", allowedBrowserOrigins);
    }
}
