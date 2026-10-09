package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.domain.capability.policy.CapabilityBinding;
import com.cloud.gateway.domain.capability.policy.CapabilityBundle;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.policy.CapabilityBindingId;
import com.cloud.gateway.domain.capability.policy.CapabilityBundleId;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.ratelimit.RedisFailureMode;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityPolicyConfigStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractAccessSnapshotDocumentMapStructMapperImpl;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.SubjectType;

import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;
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
import com.cloud.gateway.domain.capability.header.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.capability.header.CookieForwardingMode;
import com.cloud.gateway.domain.capability.header.CredentialForwarding;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.framework.core.error.FrameworkException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityPolicyConfigStorageTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HeaderConfigRule headerRules = HeaderConfigRule.empty();
    private final StoredAccessSnapshotMapper mapper = new DefaultStoredAccessSnapshotMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(headerRules), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AbstractAccessSnapshotDocumentMapStructMapperImpl(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(headerRules), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
    private final StoredPolicyDefinitionCodec definitions = new StoredPolicyDefinitionCodec(mapper, json, BuiltInCapabilityCatalog.validator(headerRules));
    private final CanonicalAccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new AbstractCapabilityPolicyConfigStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(BuiltInCapabilityCatalog.validator(headerRules), new com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), json);

    @Test
    void shouldRejectInvalidSnapshotBindingReferencesWhileAllowingDefinitionEditing() throws java.io.IOException {
        CapabilityBundleId id = new CapabilityBundleId("headers");
        CapabilityBundle bundle = new CapabilityBundle(id, List.of(HeaderConfig.defaults()));
        for (CapabilityBinding binding : List.of(
                new CapabilityBinding(new CapabilityBindingId("missing-app"), id, null, null, Set.of(),
                        new AppId("missing"), null, null),
                new CapabilityBinding(new CapabilityBindingId("missing-client"), id, null, null, Set.of(),
                        null, new ClientId("missing"), null),
                new CapabilityBinding(new CapabilityBindingId("missing-endpoint"), id, null,
                        new EndpointId("missing"), Set.of(), null, null, null),
                new CapabilityBinding(new CapabilityBindingId("missing-service"), id, new ServiceId("missing"),
                        null, Set.of(), null, null, null),
                new CapabilityBinding(new CapabilityBindingId("missing-tag"), id, null, null,
                        Set.of("missing"), null, null, null))) {
            assertSnapshotRejected(new CapabilityPolicyConfig(List.of(bundle), List.of(binding)));
        }
    }

    @Test
    void shouldRejectSamePrioritySnapshotConflictsEvenWithACorrectContentHash() throws java.io.IOException {
        CapabilityBundleId first = new CapabilityBundleId("first");
        CapabilityBundleId second = new CapabilityBundleId("second");
        CapabilityPolicyConfig policyConfig = new CapabilityPolicyConfig(List.of(
                new CapabilityBundle(first, List.of(HeaderConfig.defaults())),
                new CapabilityBundle(second, List.of(new HeaderConfig(Set.of(), Map.of(), Set.of(), null)))),
                List.of(new CapabilityBinding(new CapabilityBindingId("first"), first,
                                null, null, Set.of(), null, null, null),
                        new CapabilityBinding(new CapabilityBindingId("second"), second,
                                null, null, Set.of(), null, null, null)));
        assertSnapshotRejected(policyConfig);
    }

    private void assertSnapshotRejected(CapabilityPolicyConfig policyConfig) throws java.io.IOException {
        // Cross-definition references and competition are publication rules, not editing constraints.
        assertEquals(policyConfig, definitions.decodeCapabilityPolicyConfig(definitions.encodeCapabilityPolicyConfig(policyConfig)));
        IngressContent content = content(policyConfig, Set.of("read", "public"));
        IngressSnapshot snapshot = new IngressSnapshot(new SnapshotVersion(3), content, hasher.hash(content), Instant.EPOCH);
        StoredAccessSnapshotCodec codec = new StoredAccessSnapshotCodec(mapper, hasher, json,
                BuiltInCapabilityCatalog.validator(headerRules));
        assertThrows(DomainException.class, () -> codec.encodeSnapshot(snapshot));
        // Simulate an external snapshot producer, bypassing the protected encoding entry point.
        String document = json.writeValueAsString(mapper.toStore(snapshot));
        InfrastructureException failure = assertThrows(InfrastructureException.class,
                () -> codec.decodeSnapshot(document));
        assertInstanceOf(DomainException.class, failure.getCause());
    }

    @Test void shouldRoundTripEveryCapabilityAndScalarIdentifier() {
        CapabilityPolicyConfig source = configuration(false);
        String yaml = definitions.encodeCapabilityPolicyConfig(source);
        assertEquals(source, definitions.decodeCapabilityPolicyConfig(yaml));
        assertTrue(yaml.contains("bundleId: \"standard\""));
        assertFalse(yaml.contains("value:"));
        assertTrue(yaml.contains("credentialForwarding:"));
        assertFalse(yaml.contains("credentials:"));
        assertThrows(InfrastructureException.class, () -> definitions.decodeCapabilityPolicyConfig(
                yaml.replace("credentialForwarding:", "credentials:")));
    }

    @Test void shouldRejectDuplicateWireKeysAndLegacyBindingArrays() {
        for (String document : List.of(
                "{\"bundles\":{},\"bundles\":{},\"bindings\":{}}",
                "{\"bundles\":{\"one\":{\"bundleId\":\"one\",\"policyConfig\":{}},\"one\":{\"bundleId\":\"one\",\"policyConfig\":{}}},\"bindings\":{}}",
                """
                bundles:
                  one: {bundleId: one, policyConfig: {}}
                bindings:
                  all: {bindingId: all, bundleId: one}
                  all: {bindingId: all, bundleId: one}
                """,
                "{\"bundles\":{},\"bindings\":[]}")) {
            assertThrows(InfrastructureException.class,
                    () -> definitions.decodeCapabilityPolicyConfig(document), document);
        }
    }

    @Test void shouldRejectUnknownNestedFieldsAndUnsupportedMode() {
        String yaml = definitions.encodeCapabilityPolicyConfig(configuration(false));
        // The tampered text must differ from the original, or the replace is a no-op and this guard
        // never runs. That is what happened when the cache node was removed: its field stopped
        // existing and the assertion silently became a tautology.
        String unknown = yaml.replace("connect:", "unknownTimeoutField:");
        assertNotEquals(yaml, unknown, "the tampered text must differ from the original");
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeCapabilityPolicyConfig(unknown));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeCapabilityPolicyConfig(yaml.replace("EXACT", "PREALLOCATED")));
    }

    @Test void shouldRejectNonIdentityHeaderSourcesAndRemovedSourceNames() {
        String yaml = definitions.encodeCapabilityPolicyConfig(configuration(false));
        for (String source : List.of("SIGNATURE", "APP_VERSION", "VERIFIED_CLIENT_ID", "SUBJECT_ID", "LEGACY_CLIENT_APP_ID")) {
            assertThrows(InfrastructureException.class,
                    () -> definitions.decodeCapabilityPolicyConfig(yaml.replace("APP_ID", source)), source);
        }
    }

    @Test void shouldRejectOldMappingFieldAndSerializeTrustedHeadersInStableOrder() {
        String forward = definitions.encodeCapabilityPolicyConfig(configuration(false));
        String reverse = definitions.encodeCapabilityPolicyConfig(configuration(true));
        assertEquals(forward, reverse);
        assertTrue(forward.contains("trustedHeaders:"));
        assertFalse(forward.contains("trustedMappings:"));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeCapabilityPolicyConfig(forward.replace("trustedHeaders:", "trustedMappings:")));
        StoredCapabilityPolicyConfig.Headers headers = json.convertValue(mapper.toStoredPolicies(configuration(false)).bundles()
                .get("standard").capabilities().get("header-apply"), StoredCapabilityPolicyConfig.Headers.class);
        assertEquals(new ArrayList<>(new TreeSet<>(headers.trustedHeaders())), new ArrayList<>(headers.trustedHeaders()));
        assertThrows(UnsupportedOperationException.class, () -> headers.trustedHeaders().clear());
    }

    @Test void shouldHashEquivalentCollectionOrdersAndIncludePolicyChangesAndTags() {
        IngressContent first = content(configuration(false), new LinkedHashSet<>(List.of("read", "public")));
        IngressContent second = content(configuration(true), new LinkedHashSet<>(List.of("public", "read")));
        assertEquals(hasher.hash(first), hasher.hash(second));
        assertNotEquals(hasher.hash(first), hasher.hash(content(CapabilityPolicyConfig.empty(), Set.of("read", "public"))));
        assertNotEquals(hasher.hash(first), hasher.hash(content(configuration(false), Set.of("read"))));
    }

    @Test void shouldRoundTripFullSnapshotWithTagsAndPolicies() {
        IngressContent content = content(configuration(false), Set.of("read", "public"));
        var snapshot = new IngressSnapshot(new SnapshotVersion(3), content, hasher.hash(content),
                Instant.parse("2026-09-08T00:00:00Z"));
        var codec = new StoredAccessSnapshotCodec(mapper, hasher, json, BuiltInCapabilityCatalog.validator(headerRules));
        IngressSnapshot restored = codec.decodeSnapshot(codec.encodeSnapshot(snapshot));
        assertEquals(content.policyConfig(), restored.content().policyConfig());
        assertEquals(Set.of("read", "public"), restored.content().endpoints().find(new EndpointId("READ")).orElseThrow().getTags());
        assertEquals(ClientStatus.ENABLED, restored.content().clients().find(new ClientId("partner")).orElseThrow().getStatus());
    }

    @Test void shouldRequireClientStatusInExternalDefinitionAndIncludeItInHash() {
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeClients("partner: {allowedApps: [web]}"));
        IngressContent source = content(CapabilityPolicyConfig.empty(), Set.of());
        var changed = new IngressContent(source.apps(),
                new ClientRegistry(List.of(new Client(new ClientId("partner"), ClientStatus.DISABLED,
                        new ClientAccess(Set.of(new AppId("web")), null)))),
                source.endpoints(), source.policyConfig());
        assertNotEquals(hasher.hash(source), hasher.hash(changed));
    }

    @Test void shouldRoundTripNestedClientCredentialsAndHashCredentialChanges() {
        var clientId = new ClientId("partner");
        var active = new Credential(new CredentialId("current"), "secret-current",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
        var grace = new Credential(new CredentialId("previous"), "secret-previous",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1,
                active.validFrom(), active.expiresAt());
        var credentials = new Credentials(active, grace);
        var clients = new ClientRegistry(List.of(new Client(clientId, ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("web")), credentials))));
        String encoded = definitions.encodeClients(clients);
        assertEquals(clients.asMap(), definitions.decodeClients(encoded).asMap());
        assertTrue(encoded.contains("credentials:"));
        assertFalse(encoded.contains("clientCredentials:"));
        IngressContent original = content(CapabilityPolicyConfig.empty(), Set.of());
        var withCredentials = new IngressContent(original.apps(), clients,
                original.endpoints(), original.policyConfig());
        assertNotEquals(hasher.hash(original), hasher.hash(withCredentials));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeClients(encoded.replace("activeCredential:", "unknownCredential:")));
    }

    @Test void shouldRequireEndpointEnumAndRejectLegacyBoolean() {
        IngressContent original = content(CapabilityPolicyConfig.empty(), Set.of());
        String encoded = definitions.encodeEndpointRegistry(original.endpoints());
        assertTrue(encoded.contains("status: \"ENABLED\""));
        assertEquals(original.endpoints().asMap(), definitions.decodeEndpointRegistry(encoded).asMap());
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeEndpointRegistry(encoded.replace("status: \"ENABLED\"", "enabled: true")));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeEndpointRegistry(encoded.replace("status: \"ENABLED\"", "status: null")));
        String changed = encoded.replace("ENABLED", "DISABLED");
        Endpoint disabled = definitions.decodeEndpointRegistry(changed).find(new EndpointId("READ")).orElseThrow();
        assertFalse(disabled.enabled());
        var changedContent = new IngressContent(original.apps(), original.clients(),
                definitions.decodeEndpointRegistry(changed), original.policyConfig());
        assertNotEquals(hasher.hash(original), hasher.hash(changedContent));
    }

    private static IngressContent content(CapabilityPolicyConfig policies, Set<String> tags) {
        var endpointId = new EndpointId("READ");
        Endpoint endpoint = new Endpoint(endpointId, new ServiceId("catalog"), new RouteId("catalog"),
                List.of(new EndpointMatcher("GET", "/read")), tags, EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
        return new IngressContent(
                new AppRegistry(List.of(new App(new AppId("web"), "WEB", AppStatus.ENABLED,
                        new AppAccess(Set.of())))),
                new ClientRegistry(List.of(new Client(new ClientId("partner"), ClientStatus.ENABLED,
                        new ClientAccess(Set.of(new AppId("web")), null)))),
                new EndpointRegistry(List.of(endpoint)), policies);
    }

    private static CapabilityPolicyConfig configuration(boolean reverse) {
        var id = new CapabilityBundleId("standard");
        var bundle = new CapabilityBundle(id, java.util.List.of(new RateLimitConfig(List.of(
                new RateLimitRule("read-quota", RateLimitDimension.ENDPOINT, 20, 10, RateLimitMode.EXACT)), RedisFailureMode.CLOSED), new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)), new HeaderConfig(new LinkedHashSet<>(reverse ? List.of(RequestHeader.CLIENT_ID, RequestHeader.APP_ID)
                        : List.of(RequestHeader.APP_ID, RequestHeader.CLIENT_ID)),
                        Map.of("X-Gateway", "v1"), new LinkedHashSet<>(reverse ? List.of("X-A", "X-B") : List.of("X-B", "X-A")),
                        new CredentialForwarding(AuthorizationHeaderForwardingMode.STRIP, CookieForwardingMode.ALLOW_LIST,
                                new LinkedHashSet<>(reverse ? List.of("a", "b") : List.of("b", "a"))))));
        return new CapabilityPolicyConfig(java.util.List.of(bundle), List.of(new CapabilityBinding(new CapabilityBindingId("read"),
                id, new ServiceId("catalog"), new EndpointId("READ"), Set.of("read"), new AppId("web"),
                new ClientId("partner"), SubjectType.HOST_SESSION)));
    }
}
