package com.cloud.gateway.infrastructure.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalIngressAccessSnapshotHasherTest {
    private static final String V1_CANONICAL_HASH =
            "9d64771acbf83122a2061f2d5fbe5d015975213b9badc0bf83ca2ca3bec6ecb7";
    private static final String FIRST_CLIENT_APP_ID = "app-a";
    private static final String SECOND_CLIENT_APP_ID = "app-b";

    private final CanonicalPolicySnapshotHasher hasher =
            new CanonicalPolicySnapshotHasher(new CanonicalPolicyContentMapper(), new ObjectMapper());

    @Test
    void shouldProduceSameHashForEquivalentMapInsertionOrders() {
        Map<AppId, AppPolicy> forward = new LinkedHashMap<>();
        forward.put(new AppId(FIRST_CLIENT_APP_ID), client(FIRST_CLIENT_APP_ID));
        forward.put(new AppId(SECOND_CLIENT_APP_ID), client(SECOND_CLIENT_APP_ID));
        Map<AppId, AppPolicy> reverse = new LinkedHashMap<>();
        reverse.put(new AppId(SECOND_CLIENT_APP_ID), client(SECOND_CLIENT_APP_ID));
        reverse.put(new AppId(FIRST_CLIENT_APP_ID), client(FIRST_CLIENT_APP_ID));

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
    void shouldProduceStableV1CanonicalHash() {
        Map<AppId, AppPolicy> apps = new LinkedHashMap<>();
        apps.put(new AppId(FIRST_CLIENT_APP_ID), client(FIRST_CLIENT_APP_ID));
        apps.put(new AppId(SECOND_CLIENT_APP_ID), client(SECOND_CLIENT_APP_ID));

        assertEquals(V1_CANONICAL_HASH, hasher.hash(content(apps)));
    }

    @Test
    void shouldIncludeNormalizedBrowserOriginsInHashIndependentOfSetOrder() {
        AppPolicy forward = client(FIRST_CLIENT_APP_ID,
                Set.of("https://one.example.com", "https://two.example.com"));
        AppPolicy reverse = client(FIRST_CLIENT_APP_ID,
                new LinkedHashSet<>(List.of(
                        "https://two.example.com", "https://one.example.com")));
        AppPolicy changed = client(FIRST_CLIENT_APP_ID,
                Set.of("https://three.example.com"));

        assertEquals(
                hasher.hash(content(Map.of(forward.appId(), forward))),
                hasher.hash(content(Map.of(reverse.appId(), reverse))));
        assertNotEquals(
                hasher.hash(content(Map.of(forward.appId(), forward))),
                hasher.hash(content(Map.of(changed.appId(), changed))));
    }

    private static IngressAccessContent content(Map<AppId, AppPolicy> apps) {
        return content(apps, List.of(new EndpointMatcher("GET", "/test")));
    }

    private static IngressAccessContent content(
            Map<AppId, AppPolicy> apps,
            List<EndpointMatcher> matchers
    ) {
        EndpointRegistry catalog = new EndpointRegistry(List.of(new Endpoint(
                new EndpointId("TEST.ENDPOINT"), "test-service",
                matchers, "test-route")));
        return new IngressAccessContent(catalog, apps, Map.of(), Map.of());
    }

    private static AppPolicy client(String appId) {
        return client(appId, Set.of());
    }

    private static AppPolicy client(String appId, Set<String> allowedOrigins) {
        return new AppPolicy(
                new AppId(appId), "TEST", allowedOrigins, PolicyStatus.ENABLED);
    }
}
