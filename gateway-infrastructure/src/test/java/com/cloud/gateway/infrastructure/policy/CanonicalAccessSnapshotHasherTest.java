package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigStorageMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalAccessSnapshotHasherTest {
    private static final String FIRST_CLIENT_APP_ID = "app-a";
    private static final String SECOND_CLIENT_APP_ID = "app-b";

    private final CanonicalAccessSnapshotHasher hasher =
            new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), new ObjectMapper());

    @Test
    void shouldProduceSameHashForEquivalentAppInsertionOrders() {
        assertEquals(
                hasher.hash(content(List.of(app(FIRST_CLIENT_APP_ID), app(SECOND_CLIENT_APP_ID)))),
                hasher.hash(content(List.of(app(SECOND_CLIENT_APP_ID), app(FIRST_CLIENT_APP_ID)))));
    }

    @Test
    void shouldProduceSameHashForEquivalentMatcherOrders() {
        List<EndpointMatcher> forward = List.of(
                new EndpointMatcher("GET", "/first"),
                new EndpointMatcher("POST", "/second"));
        List<EndpointMatcher> reverse = List.of(
                new EndpointMatcher("POST", "/second"),
                new EndpointMatcher("GET", "/first"));

        assertEquals(hasher.hash(content(List.of(), forward)), hasher.hash(content(List.of(), reverse)));
    }

    @Test
    void shouldProduceSameHashForEquivalentEndpointInsertionOrders() {
        Endpoint first = endpoint("TEST.FIRST", List.of(new EndpointMatcher("GET", "/first")));
        Endpoint second = endpoint("TEST.SECOND", List.of(new EndpointMatcher("GET", "/second")));

        assertEquals(hasher.hash(contentWithEndpoints(List.of(), List.of(first, second))),
                hasher.hash(contentWithEndpoints(List.of(), List.of(second, first))));
    }

    @Test
    void shouldIncludeNormalizedBrowserOriginsInHashIndependentOfSetOrder() {
        App forward = app(FIRST_CLIENT_APP_ID,
                Set.of("https://one.example.com", "https://two.example.com"));
        App reverse = app(FIRST_CLIENT_APP_ID,
                new LinkedHashSet<>(List.of(
                        "https://two.example.com", "https://one.example.com")));
        App changed = app(FIRST_CLIENT_APP_ID, Set.of("https://three.example.com"));

        assertEquals(hasher.hash(content(List.of(forward))), hasher.hash(content(List.of(reverse))));
        assertNotEquals(hasher.hash(content(List.of(forward))), hasher.hash(content(List.of(changed))));
    }

    @Test
    void shouldChangeHashWhenEndpointAccessChanges() {
        EndpointAccess required = new EndpointAccess(ClientVerificationRequirement.REQUIRED,
                SubjectAuthenticationRequirement.REQUIRED, Set.of("admin"), Set.of("gateway"));
        EndpointAccess optional = new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of());

        assertNotEquals(
                hasher.hash(contentWithAccess(required)),
                hasher.hash(contentWithAccess(optional)));
    }

    private static IngressContent content(List<App> apps) {
        return content(apps, List.of(new EndpointMatcher("GET", "/test")));
    }

    private static IngressContent content(List<App> apps, List<EndpointMatcher> matchers) {
        return contentWithEndpoints(apps, List.of(endpoint("TEST.ENDPOINT", matchers)));
    }

    private static IngressContent contentWithEndpoints(List<App> apps, List<Endpoint> endpoints) {
        return new IngressContent(new AppRegistry(apps), new ClientRegistry(List.of()),
                new EndpointRegistry(endpoints), CapabilityPolicyConfig.empty());
    }

    private static IngressContent contentWithAccess(EndpointAccess access) {
        return new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(new Endpoint(new EndpointId("TEST.ENDPOINT"),
                        new ServiceId("test-service"), new RouteId("test-route"),
                        List.of(new EndpointMatcher("GET", "/test")), Set.of(),
                        EndpointStatus.ENABLED, access))),
                CapabilityPolicyConfig.empty());
    }

    private static Endpoint endpoint(String endpointId, List<EndpointMatcher> matchers) {
        return new Endpoint(new EndpointId(endpointId), new ServiceId("test-service"),
                new RouteId("test-route"), matchers, Set.of(), EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.REQUIRED,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
    }

    private static App app(String appId) {
        return app(appId, Set.of());
    }

    private static App app(String appId, Set<String> allowedOrigins) {
        return new App(new AppId(appId), "TEST", AppStatus.ENABLED, new AppAccess(allowedOrigins));
    }
}
