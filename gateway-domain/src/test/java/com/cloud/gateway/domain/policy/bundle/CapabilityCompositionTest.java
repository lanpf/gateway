package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.RequestHeader;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
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
import com.cloud.gateway.domain.policy.execution.CapabilityContext;
import com.cloud.gateway.domain.policy.execution.CapabilitySlot;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.app.VerifiedApp;
import com.cloud.gateway.domain.ingress.access.client.VerifiedClient;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationMode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityCompositionTest {
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("PRODUCT.GET"),
            new ServiceId("product"), new RouteId("product-api"),
            List.of(new EndpointMatcher("GET", "/products/**")), Set.of("public-read"), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                    SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));

    @Test void shouldResolveBundleWithinItsOwnConfiguration() {
        var id = new CapabilityBundleId("standard");
        HeaderPolicy oldHeaders = HeaderPolicy.defaults(CredentialForwarding.stripAll());
        var newHeaders = new HeaderPolicy(Set.of(RequestHeader.APP_ID),
                Map.of(), Set.of(), null);
        CapabilityBinding binding = binding("all", id, null);
        var oldConfig = new CapabilityComposition(Map.of(id, bundle(id, oldHeaders)), List.of(binding));
        var newConfig = new CapabilityComposition(Map.of(id, bundle(id, newHeaders)), List.of(binding));
        var resolver = new CapabilityResolver();
        assertEquals(oldHeaders, resolver.resolve(oldConfig, context(null)).headers());
        assertEquals(newHeaders, resolver.resolve(newConfig, context(null)).headers());
        assertEquals(oldHeaders, resolver.resolve(oldConfig, context(null)).headers());
    }

    @Test void shouldRejectDanglingBundleReference() {
        assertThrows(DomainException.class, () -> new CapabilityComposition(Map.of(),
                List.of(binding("all", new CapabilityBundleId("missing"), null))));
    }

    @Test void shouldRejectOverlappingBindingsAtSameLevel() {
        var id = new CapabilityBundleId("standard");
        var config = new CapabilityComposition(Map.of(id, bundle(id, HeaderPolicy.defaults(null))),
                List.of(binding("one", id, null), binding("two", id, null)));
        assertThrows(DomainException.class, () -> config.validate(content(config)));
    }

    @Test void shouldMatchClientOnlyWhenVerified() {
        var id = new CapabilityBundleId("standard");
        var distinctive = new HeaderPolicy(Set.of(RequestHeader.APP_ID), Map.of(), Set.of(), null);
        var config = new CapabilityComposition(Map.of(id, bundle(id, distinctive)),
                List.of(binding("partner", id, new ClientId("partner"))));
        var resolver = new CapabilityResolver();
        assertEquals(HeaderPolicy.defaults(null),
                resolver.resolve(config, context(null)).headers());
        assertEquals(distinctive,
                resolver.resolve(config, context(new ClientId("partner"))).headers());
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

    private static CapabilityContext context(ClientId client) {
        return new CapabilityContext(CapabilitySlot.POST_AUTH,
                new RequestEvidence("GET", "/products/one", null, null, null, null, "ip"), ENDPOINT,
                new AccessGrantedContext(new VerifiedApp(new AppId("app"), "web"),
                        client == null ? null : new VerifiedClient(client, new CredentialId("credential"),
                                ClientVerificationMode.REQUEST_SIGNATURE, Instant.EPOCH), null));
    }

    private static CapabilityBundle bundle(CapabilityBundleId id, HeaderPolicy headers) {
        return new CapabilityBundle(id, null, null, null, null, headers);
    }
    private static CapabilityBinding binding(String id, CapabilityBundleId bundleId, ClientId client) {
        return new CapabilityBinding(new CapabilityBindingId(id), bundleId, null, null, Set.of(), null, client, null);
    }
    private static IngressContent content(CapabilityComposition config) {
        return new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(ENDPOINT)), config);
    }
}
