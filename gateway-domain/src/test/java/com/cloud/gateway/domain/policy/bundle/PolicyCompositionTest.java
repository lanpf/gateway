package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.RequestHeader;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.policy.header.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PolicyConfigTest {
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("PRODUCT.GET"),
            new ServiceId("product"), new RouteId("product-api"),
            List.of(new EndpointMatcher("GET", "/products/**")), Set.of("public-read"), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                    SubjectAuthenticationRequirement.OPTIONAL, Set.of(), Set.of()));

    @Test void shouldResolveBundleWithinItsOwnConfiguration() {
        var id = new PolicyBundleId("standard");
        HeaderPolicy oldHeaders = HeaderPolicy.defaults(CredentialForwarding.stripAll());
        var newHeaders = new HeaderPolicy(Set.of(RequestHeader.APP_ID),
                Map.of(), Set.of(), null);
        PolicyBinding binding = binding("all", id, null);
        var oldConfig = new PolicyConfig(Map.of(id, bundle(id, oldHeaders)), List.of(binding));
        var newConfig = new PolicyConfig(Map.of(id, bundle(id, newHeaders)), List.of(binding));
        var resolver = new PolicyResolver();
        assertEquals(oldHeaders, resolver.resolve(oldConfig, ENDPOINT, null, null, null).headers());
        assertEquals(newHeaders, resolver.resolve(newConfig, ENDPOINT, null, null, null).headers());
        assertEquals(oldHeaders, resolver.resolve(oldConfig, ENDPOINT, null, null, null).headers());
    }

    @Test void shouldRejectDanglingBundleReference() {
        assertThrows(DomainException.class, () -> new PolicyConfig(Map.of(),
                List.of(binding("all", new PolicyBundleId("missing"), null))));
    }

    @Test void shouldRejectOverlappingBindingsAtSameLevel() {
        var id = new PolicyBundleId("standard");
        var config = new PolicyConfig(Map.of(id, bundle(id, HeaderPolicy.defaults(null))),
                List.of(binding("one", id, null), binding("two", id, null)));
        assertThrows(DomainException.class, () -> config.validate(content(config)));
    }

    @Test void shouldMatchClientOnlyWhenVerified() {
        var id = new PolicyBundleId("standard");
        var distinctive = new HeaderPolicy(Set.of(RequestHeader.APP_ID), Map.of(), Set.of(), null);
        var config = new PolicyConfig(Map.of(id, bundle(id, distinctive)),
                List.of(binding("partner", id, new ClientId("partner"))));
        var resolver = new PolicyResolver();
        assertEquals(HeaderPolicy.defaults(null),
                resolver.resolve(config, ENDPOINT, null, null, null).headers());
        assertEquals(distinctive,
                resolver.resolve(config, ENDPOINT, null, new ClientId("partner"), null).headers());
    }

    @Test void shouldRejectSpoofableHeaderConfiguration() {
        assertThrows(DomainException.class, () -> new HeaderPolicy(
                Set.of(RequestHeader.APP_VERSION), Map.of(), Set.of(), null));
        // Name admission belongs to deployment HeaderRules; HeaderPolicy only validates structure.
        var unadmitted = new HeaderPolicy(Set.of(), Map.of("x-user-endpointId", "admin"), Set.of(), null);
        assertThrows(DomainException.class, () -> HeaderRules.registeredOnly().validate(unadmitted));
        assertThrows(DomainException.class, () -> new HeaderPolicy(
                Set.of(), Map.of("X-Test", "first", "x-test", "override"), Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderPolicy(
                Set.of(), Map.of("X-Test", "value\r\nInjected: yes"), Set.of(), null));
    }

    private static PolicyBundle bundle(PolicyBundleId id, HeaderPolicy headers) {
        return new PolicyBundle(id, null, null, null, null, headers);
    }
    private static PolicyBinding binding(String id, PolicyBundleId bundleId, ClientId client) {
        return new PolicyBinding(new PolicyBindingId(id), bundleId, null, null, Set.of(), null, client, null);
    }
    private static IngressAccessContent content(PolicyConfig config) {
        return new IngressAccessContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(ENDPOINT)), config);
    }
}
