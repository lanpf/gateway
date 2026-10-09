package com.cloud.gateway.domain.capability.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.framework.core.RequestHeader;

import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.framework.core.error.FrameworkException;
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
import com.cloud.gateway.domain.capability.header.CredentialForwarding;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.domain.ingress.access.client.VerifiedClient;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationMode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityPolicyConfigTest {
    private static final Endpoint ENDPOINT = new Endpoint(new EndpointId("PRODUCT.GET"),
            new ServiceId("product"), new RouteId("product-api"),
            List.of(new EndpointMatcher("GET", "/products/**")), Set.of("public-read"), EndpointStatus.ENABLED,
            new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                    SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));

    @Test void shouldResolveBundleWithinItsOwnConfiguration() {
        var id = new CapabilityBundleId("standard");
        HeaderConfig oldHeaders = HeaderConfig.defaults(CredentialForwarding.stripAll());
        var newHeaders = new HeaderConfig(Set.of(RequestHeader.APP_ID),
                Map.of(), Set.of(), null);
        CapabilityBinding binding = binding("all", id, null);
        var oldConfig = new CapabilityPolicyConfig(java.util.List.of(bundle(id, oldHeaders)), List.of(binding));
        var newConfig = new CapabilityPolicyConfig(java.util.List.of(bundle(id, newHeaders)), List.of(binding));
        CapabilityPolicyAssembler assembler = BuiltInCapabilityCatalog.assembler();
        assertEquals(oldHeaders, assembler.assemble(oldConfig, context(null).endpoint(), context(null).granted()).get(EffectiveHeaderPolicy.class).config());
        assertEquals(newHeaders, assembler.assemble(newConfig, context(null).endpoint(), context(null).granted()).get(EffectiveHeaderPolicy.class).config());
        assertEquals(oldHeaders, assembler.assemble(oldConfig, context(null).endpoint(), context(null).granted()).get(EffectiveHeaderPolicy.class).config());
    }

    @Test void shouldRejectDanglingBundleReference() {
        assertThrows(DomainException.class, () -> new CapabilityPolicyConfig(java.util.List.of(),
                List.of(binding("all", new CapabilityBundleId("missing"), null))));
    }

    /**
     * Two <em>distinct</em> bundles competing for the same overridden node is the ambiguity to reject.
     * The same bundle reached by two bindings is not: see {@code CapabilityPolicyConfigConflictTest}.
     */
    @Test void shouldRejectOverlappingBindingsOfDistinctBundlesAtSameLevel() {
        var first = new CapabilityBundleId("standard");
        var second = new CapabilityBundleId("other");
        var config = new CapabilityPolicyConfig(java.util.List.of(bundle(first, HeaderConfig.defaults(null)), bundle(second, HeaderConfig.defaults(null))),
                List.of(binding("one", first, null), binding("two", second, null)));
        IngressContent content = content(config);
        assertThrows(DomainException.class,
                () -> BuiltInCapabilityCatalog.validator()
                        .validate(config, content.apps(), content.clients(), content.endpoints()));
    }

    @Test void shouldMatchClientOnlyWhenVerified() {
        var id = new CapabilityBundleId("standard");
        var distinctive = new HeaderConfig(Set.of(RequestHeader.APP_ID), Map.of(), Set.of(), null);
        var config = new CapabilityPolicyConfig(java.util.List.of(bundle(id, distinctive)),
                List.of(binding("partner", id, new ClientId("partner"))));
        CapabilityPolicyAssembler assembler = BuiltInCapabilityCatalog.assembler();
        assertEquals(HeaderConfig.defaults(null),
                assembler.assemble(config, context(null).endpoint(), context(null).granted()).get(EffectiveHeaderPolicy.class).config());
        assertEquals(distinctive,
                assembler.assemble(config, context(new ClientId("partner")).endpoint(), context(new ClientId("partner")).granted()).get(EffectiveHeaderPolicy.class).config());
    }

    @Test void shouldRejectSpoofableHeaderConfiguration() {
        assertThrows(DomainException.class, () -> new HeaderConfig(
                Set.of(RequestHeader.APP_VERSION), Map.of(), Set.of(), null));
        // Deployment rules carry only deny lists; any legal token name outside them may be operated.
        var ordinary = new HeaderConfig(Set.of(), Map.of("x-user-endpointId", "admin"), Set.of(), null);
        assertDoesNotThrow(() -> HeaderConfigRule.empty().validate(List.of(ordinary)));
        assertThrows(DomainException.class,
                () -> new HeaderConfigRule(Set.of("x-user-endpointid"), Set.of()).validate(List.of(ordinary)));
        assertThrows(DomainException.class, () -> new HeaderConfig(
                Set.of(), Map.of("X-Test", "first", "x-test", "override"), Set.of(), null));
        assertThrows(DomainException.class, () -> new HeaderConfig(
                Set.of(), Map.of("X-Test", "value\r\nInjected: yes"), Set.of(), null));
    }

    private static CapabilityContext context(ClientId client) {
        return new CapabilityContext(CapabilitySlot.POST_AUTH,
                new RequestEvidence("GET", "/products/one", null, null, null, null, "ip"), ENDPOINT,
                new AccessGrantedContext(new VerifiedApp(new AppId("app"), "web"),
                        client == null ? null : new VerifiedClient(client, new CredentialId("credential"),
                                ClientVerificationMode.REQUEST_SIGNATURE, Instant.EPOCH), null));
    }

    private static CapabilityBundle bundle(CapabilityBundleId id, HeaderConfig headers) {
        return new CapabilityBundle(id, java.util.List.of(headers));
    }
    private static CapabilityBinding binding(String id, CapabilityBundleId bundleId, ClientId client) {
        return new CapabilityBinding(new CapabilityBindingId(id), bundleId, null, null, Set.of(), null, client, null);
    }
    private static IngressContent content(CapabilityPolicyConfig config) {
        return new IngressContent(new AppRegistry(List.of()), new ClientRegistry(List.of()),
                new EndpointRegistry(List.of(ENDPOINT)), config);
    }
}
