package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.configuration.CapabilityBinding;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundle;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.configuration.CapabilityBindingId;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundleId;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicy;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.ratelimit.RedisFailureMode;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.cloud.gateway.domain.capability.header.HeaderPolicy;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicy;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

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
import com.cloud.gateway.domain.capability.configuration.*;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityCompositionStorageTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final StoredAccessSnapshotMapper mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
    private final HeaderAdmissionRules headerRules = new HeaderAdmissionRules(Set.of("X-Gateway", "X-A", "X-B"), Set.of(), Set.of());
    private final StoredPolicyDefinitionCodec definitions = new StoredPolicyDefinitionCodec(mapper, json, headerRules);
    private final CanonicalAccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(new DefaultCanonicalPolicyContentMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), json);

    @Test void shouldRoundTripEveryCapabilityAndScalarIdentifier() {
        CapabilityComposition source = configuration(false);
        String yaml = definitions.encodeCapabilityComposition(source);
        assertEquals(source, definitions.decodeCapabilityComposition(yaml));
        assertTrue(yaml.contains("bundleId: \"standard\""));
        assertFalse(yaml.contains("value:"));
        assertTrue(yaml.contains("credentialForwarding:"));
        assertFalse(yaml.contains("credentials:"));
        assertThrows(InfrastructureException.class, () -> definitions.decodeCapabilityComposition(
                yaml.replace("credentialForwarding:", "credentials:")));
    }

    @Test void shouldRejectUnknownNestedFieldsAndUnsupportedMode() {
        String yaml = definitions.encodeCapabilityComposition(configuration(false));
        // The tampered text must differ from the original, or the replace is a no-op and this guard
        // never runs. That is what happened when the cache node was removed: its field stopped
        // existing and the assertion silently became a tautology.
        String unknown = yaml.replace("connect:", "unknownTimeoutField:");
        assertNotEquals(yaml, unknown, "the tampered text must differ from the original");
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeCapabilityComposition(unknown));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeCapabilityComposition(yaml.replace("EXACT", "PREALLOCATED")));
    }

    @Test void shouldRejectNonIdentityHeaderSourcesAndRemovedSourceNames() {
        String yaml = definitions.encodeCapabilityComposition(configuration(false));
        for (String source : List.of("SIGNATURE", "APP_VERSION", "VERIFIED_CLIENT_ID", "SUBJECT_ID", "LEGACY_CLIENT_APP_ID")) {
            assertThrows(InfrastructureException.class,
                    () -> definitions.decodeCapabilityComposition(yaml.replace("APP_ID", source)), source);
        }
    }

    @Test void shouldRejectOldMappingFieldAndSerializeTrustedHeadersInStableOrder() {
        String forward = definitions.encodeCapabilityComposition(configuration(false));
        String reverse = definitions.encodeCapabilityComposition(configuration(true));
        assertEquals(forward, reverse);
        assertTrue(forward.contains("trustedHeaders:"));
        assertFalse(forward.contains("trustedMappings:"));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeCapabilityComposition(forward.replace("trustedHeaders:", "trustedMappings:")));
        StoredCapabilityComposition.Headers headers = json.convertValue(mapper.toStoredPolicies(configuration(false)).bundles()
                .get("standard").capabilities().get("header-apply"), StoredCapabilityComposition.Headers.class);
        assertEquals(new ArrayList<>(new TreeSet<>(headers.trustedHeaders())), new ArrayList<>(headers.trustedHeaders()));
        assertThrows(UnsupportedOperationException.class, () -> headers.trustedHeaders().clear());
    }

    @Test void shouldHashEquivalentCollectionOrdersAndIncludePolicyChangesAndTags() {
        IngressContent first = content(configuration(false), new LinkedHashSet<>(List.of("read", "public")));
        IngressContent second = content(configuration(true), new LinkedHashSet<>(List.of("public", "read")));
        assertEquals(hasher.hash(first), hasher.hash(second));
        assertNotEquals(hasher.hash(first), hasher.hash(content(CapabilityComposition.empty(), Set.of("read", "public"))));
        assertNotEquals(hasher.hash(first), hasher.hash(content(configuration(false), Set.of("read"))));
    }

    @Test void shouldRoundTripFullSnapshotWithTagsAndPolicies() {
        IngressContent content = content(configuration(false), Set.of("read", "public"));
        var snapshot = new IngressSnapshot(new SnapshotVersion(3), content, hasher.hash(content),
                Instant.parse("2026-09-08T00:00:00Z"));
        var codec = new StoredAccessSnapshotCodec(mapper, hasher, json, headerRules);
        IngressSnapshot restored = codec.decodeSnapshot(codec.encodeSnapshot(snapshot));
        assertEquals(content.capabilities(), restored.content().capabilities());
        assertEquals(Set.of("read", "public"), restored.content().endpoints().find(new EndpointId("READ")).orElseThrow().tags());
        assertEquals(ClientStatus.ENABLED, restored.content().clients().find(new ClientId("partner")).orElseThrow().status());
    }

    @Test void shouldRequireClientStatusInExternalDefinitionAndIncludeItInHash() {
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeClients("partner: {allowedApps: [web]}"));
        IngressContent source = content(CapabilityComposition.empty(), Set.of());
        var changed = new IngressContent(source.apps(),
                new ClientRegistry(List.of(new Client(new ClientId("partner"), ClientStatus.DISABLED,
                        new ClientAccess(Set.of(new AppId("web")), null)))),
                source.endpoints(), source.capabilities());
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
        IngressContent original = content(CapabilityComposition.empty(), Set.of());
        var withCredentials = new IngressContent(original.apps(), clients,
                original.endpoints(), original.capabilities());
        assertNotEquals(hasher.hash(original), hasher.hash(withCredentials));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeClients(encoded.replace("activeCredential:", "unknownCredential:")));
    }

    @Test void shouldRequireEndpointEnumAndRejectLegacyBoolean() {
        IngressContent original = content(CapabilityComposition.empty(), Set.of());
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
                definitions.decodeEndpointRegistry(changed), original.capabilities());
        assertNotEquals(hasher.hash(original), hasher.hash(changedContent));
    }

    private static IngressContent content(CapabilityComposition policies, Set<String> tags) {
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

    private static CapabilityComposition configuration(boolean reverse) {
        var id = new CapabilityBundleId("standard");
        var bundle = new CapabilityBundle(id, java.util.List.of(new RateLimitPolicy(List.of(
                new RateLimitRule("read-quota", RateLimitDimension.ENDPOINT, 20, 10, RateLimitMode.EXACT)), RedisFailureMode.CLOSED), new TimeoutPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)), new HeaderPolicy(new LinkedHashSet<>(reverse ? List.of(RequestHeader.CLIENT_ID, RequestHeader.APP_ID)
                        : List.of(RequestHeader.APP_ID, RequestHeader.CLIENT_ID)),
                        Map.of("X-Gateway", "v1"), new LinkedHashSet<>(reverse ? List.of("X-A", "X-B") : List.of("X-B", "X-A")),
                        new CredentialForwarding(AuthorizationHeaderForwardingMode.STRIP, CookieForwardingMode.ALLOW_LIST,
                                new LinkedHashSet<>(reverse ? List.of("a", "b") : List.of("b", "a"))))));
        return new CapabilityComposition(Map.of(id, bundle), List.of(new CapabilityBinding(new CapabilityBindingId("read"),
                id, new ServiceId("catalog"), new EndpointId("READ"), Set.of("read"), new AppId("web"),
                new ClientId("partner"), SubjectType.HOST_SESSION)));
    }
}
