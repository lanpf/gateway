package com.cloud.gateway.infrastructure.policy;

import com.cloud.framework.core.RequestHeader;

import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.policy.header.*;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyConfigStorageTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final StoredAccessSnapshotMapper mapper = new StoredAccessSnapshotMapper();
    private final HeaderRules headerRules = new HeaderRules(Set.of("X-Gateway", "X-A", "X-B"), Set.of(), Set.of());
    private final StoredPolicyDefinitionCodec definitions = new StoredPolicyDefinitionCodec(mapper, json, headerRules);
    private final CanonicalAccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(new CanonicalPolicyContentMapper(), json);

    @Test void shouldRoundTripEveryCapabilityAndScalarIdentifier() {
        PolicyConfig source = configuration(false);
        String yaml = definitions.encodePolicyConfiguration(source);
        assertEquals(source, definitions.decodePolicyConfiguration(yaml));
        assertTrue(yaml.contains("bundleId: \"standard\""));
        assertFalse(yaml.contains("value:"));
        assertTrue(yaml.contains("credentialForwarding:"));
        assertFalse(yaml.contains("credentials:"));
        assertThrows(InfrastructureException.class, () -> definitions.decodePolicyConfiguration(
                yaml.replace("credentialForwarding:", "credentials:")));
    }

    @Test void shouldRejectUnknownNestedFieldsAndUnsupportedMode() {
        String yaml = definitions.encodePolicyConfiguration(configuration(false));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodePolicyConfiguration(yaml.replace("maxBodyBytes:", "unknownCacheField:")));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodePolicyConfiguration(yaml.replace("EXACT", "PREALLOCATED")));
    }

    @Test void shouldRejectNonIdentityHeaderSourcesAndRemovedSourceNames() {
        String yaml = definitions.encodePolicyConfiguration(configuration(false));
        for (String source : List.of("SIGNATURE", "APP_VERSION", "VERIFIED_CLIENT_ID", "SUBJECT_ID", "LEGACY_CLIENT_APP_ID")) {
            assertThrows(InfrastructureException.class,
                    () -> definitions.decodePolicyConfiguration(yaml.replace("APP_ID", source)), source);
        }
    }

    @Test void shouldRejectOldMappingFieldAndSerializeTrustedHeadersInStableOrder() {
        String forward = definitions.encodePolicyConfiguration(configuration(false));
        String reverse = definitions.encodePolicyConfiguration(configuration(true));
        assertEquals(forward, reverse);
        assertTrue(forward.contains("trustedHeaders:"));
        assertFalse(forward.contains("trustedMappings:"));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodePolicyConfiguration(forward.replace("trustedHeaders:", "trustedMappings:")));
        StoredPolicyConfig.Headers headers = mapper.toStoredPolicies(configuration(false)).bundles()
                .get("standard").headers();
        assertEquals(new ArrayList<>(new TreeSet<>(headers.trustedHeaders())), new ArrayList<>(headers.trustedHeaders()));
        assertThrows(UnsupportedOperationException.class, () -> headers.trustedHeaders().clear());
    }

    @Test void shouldHashEquivalentCollectionOrdersAndIncludePolicyChangesAndTags() {
        IngressAccessContent first = content(configuration(false), new LinkedHashSet<>(List.of("read", "public")));
        IngressAccessContent second = content(configuration(true), new LinkedHashSet<>(List.of("public", "read")));
        assertEquals(hasher.hash(first), hasher.hash(second));
        assertNotEquals(hasher.hash(first), hasher.hash(content(PolicyConfig.empty(), Set.of("read", "public"))));
        assertNotEquals(hasher.hash(first), hasher.hash(content(configuration(false), Set.of("read"))));
    }

    @Test void shouldRoundTripFullSnapshotWithTagsAndPolicies() {
        IngressAccessContent content = content(configuration(false), Set.of("read", "public"));
        var snapshot = new IngressAccessSnapshot(3, content, hasher.hash(content), Instant.parse("2026-09-08T00:00:00Z"));
        var codec = new StoredAccessSnapshotCodec(mapper, hasher, json, headerRules);
        IngressAccessSnapshot restored = codec.decodeSnapshot(codec.encodeSnapshot(snapshot));
        assertEquals(content.policies(), restored.content().policies());
        assertEquals(Set.of("read", "public"), restored.content().endpointRegistry().find(new EndpointId("READ")).orElseThrow().tags());
        assertEquals(ClientStatus.ENABLED, restored.content().clients().get(new ClientId("partner")).status());
    }

    @Test void shouldRequireClientStatusInExternalDefinitionAndIncludeItInHash() {
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeClients("partner: {allowedApps: [web]}"));
        IngressAccessContent source = content(PolicyConfig.empty(), Set.of());
        var changed = new IngressAccessContent(source.endpointRegistry(), source.appPolicies(),
                Map.of(new ClientId("partner"), new ClientAccess(new ClientId("partner"), Set.of(new AppId("web")), ClientStatus.DISABLED)),
                source.endpointPolicies(), source.policies());
        assertNotEquals(hasher.hash(source), hasher.hash(changed));
    }

    @Test void shouldRoundTripNestedClientCredentialsAndHashCredentialChanges() {
        var clientId = new ClientId("partner");
        var active = new Credential(
                new CredentialId("current"), clientId,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-current",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
        var grace = new Credential(
                new CredentialId("previous"), clientId,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-previous",
                active.validFrom(), active.expiresAt());
        var credentials = new Credentials(active, grace);
        var client = new ClientAccess(clientId, Set.of(new AppId("web")), credentials, ClientStatus.ENABLED);
        String encoded = definitions.encodeClients(Map.of(clientId, client));
        assertEquals(Map.of(clientId, client), definitions.decodeClients(encoded));
        assertTrue(encoded.contains("credentials:"));
        assertFalse(encoded.contains("clientCredentials:"));
        IngressAccessContent original = content(PolicyConfig.empty(), Set.of());
        var withCredentials = new IngressAccessContent(original.endpointRegistry(), original.appPolicies(),
                Map.of(clientId, client), original.endpointPolicies(), original.policies());
        assertNotEquals(hasher.hash(original), hasher.hash(withCredentials));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeClients(encoded.replace("activeCredential:", "unknownCredential:")));
    }

    @Test void shouldRequireEndpointEnumAndRejectLegacyBoolean() {
        IngressAccessContent original = content(PolicyConfig.empty(), Set.of());
        String encoded = definitions.encodeEndpointAccess(original.endpointPolicies());
        assertTrue(encoded.contains("status: \"ENABLED\""));
        assertEquals(original.endpointPolicies(), definitions.decodeEndpointAccess(encoded));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeEndpointAccess(encoded.replace("status: \"ENABLED\"", "enabled: true")));
        assertThrows(InfrastructureException.class,
                () -> definitions.decodeEndpointAccess(encoded.replace("status: \"ENABLED\"", "status: null")));
        EndpointAccess disabled = original.endpointPolicies().get(new EndpointId("READ"));
        String changed = encoded.replace("ENABLED", "DISABLED");
        assertFalse(definitions.decodeEndpointAccess(changed).get(disabled.endpointId()).enabled());
        var changedContent = new IngressAccessContent(original.endpointRegistry(), original.appPolicies(),
                original.clients(), definitions.decodeEndpointAccess(changed), original.policies());
        assertNotEquals(hasher.hash(original), hasher.hash(changedContent));
    }

    private static IngressAccessContent content(PolicyConfig policies, Set<String> tags) {
        var endpoint = new EndpointId("READ");
        return new IngressAccessContent(new EndpointRegistry(List.of(new Endpoint(endpoint,
                "catalog", List.of(new EndpointMatcher("GET", "/read")), "catalog", tags))),
                Map.of(new AppId("web"), new AppAccess(new AppId("web"), "WEB", Set.of(), PolicyStatus.ENABLED)),
                Map.of(new ClientId("partner"), new ClientAccess(new ClientId("partner"), Set.of(new AppId("web")), ClientStatus.ENABLED)),
                Map.of(endpoint, new EndpointAccess(endpoint, ClientVerificationRequirement.OPTIONAL, ClientVerificationMode.IGNORE, SubjectAuthenticationRequirement.IGNORE, Set.of(), Set.of(), CredentialForwarding.stripAll(), EndpointStatus.ENABLED)), policies);
    }

    private static PolicyConfig configuration(boolean reverse) {
        var id = new PolicyBundleId("standard");
        var bundle = new PolicyBundle(id, new RateLimitRules(List.of(
                new RateLimitRule("read-quota", RateLimitDimension.ENDPOINT, 20, 10, RateLimitMode.EXACT)), RedisFailurePolicy.CLOSED),
                new TimeoutPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)),
                new CircuitBreakerPolicy(50, 50, Duration.ofSeconds(1), 10, 20, 2, Duration.ofSeconds(5), CircuitBreakerPolicy.Fallback.ERROR),
                new CachePolicy(Duration.ofSeconds(10), Duration.ZERO, 1024,
                        new LinkedHashSet<>(reverse ? List.of("accept", "accept-language") : List.of("accept-language", "accept"))),
                new HeaderPolicy(new LinkedHashSet<>(reverse ? List.of(RequestHeader.CLIENT_ID, RequestHeader.APP_ID)
                        : List.of(RequestHeader.APP_ID, RequestHeader.CLIENT_ID)),
                        Map.of("X-Gateway", "v1"), new LinkedHashSet<>(reverse ? List.of("X-A", "X-B") : List.of("X-B", "X-A")),
                        new CredentialForwarding(AuthorizationHeaderForwardingMode.STRIP, CookieForwardingMode.ALLOW_LIST,
                                new LinkedHashSet<>(reverse ? List.of("a", "b") : List.of("b", "a")))));
        return new PolicyConfig(Map.of(id, bundle), List.of(new PolicyBinding(new PolicyBindingId("read"),
                id, "catalog", new EndpointId("READ"), Set.of("read"), new AppId("web"),
                new ClientId("partner"), SubjectType.HOST_SESSION)));
    }
}
