package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class NacosPolicyDefinitionRepositoriesTest {
    private final NacosPolicyProperties properties = new NacosPolicyProperties();
    private final StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
            new StoredPolicySnapshotMapper(), new ObjectMapper().findAndRegisterModules(),
            com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());

    @Test
    void shouldLoadAndUpdateOnlyRouteDefinitionDataId() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations();
        IngressAccessContent content = PolicyTestFixtures.snapshot(1, true).content();
        operations.values.put(properties.getRouteAccessDefinitionDataId(),
                codec.encodeEndpointAccess(content.endpointPolicies()));
        NacosEndpointAccessPolicyDefinitionRepository repository =
                new NacosEndpointAccessPolicyDefinitionRepository(operations, properties, codec);

        VersionedPolicyDefinition<Map<EndpointId, EndpointPolicy>> observed = repository.load();
        Map<EndpointId, EndpointPolicy> updated =
                PolicyTestFixtures.snapshot(2, false).content().endpointPolicies();

        assertTrue(repository.compareAndSet(observed.sourceRevision(), updated));
        assertFalse(repository.load().value().get(new EndpointId("USER_AUTH.LOGIN")).enabled());
        assertEquals(2, operations.readDataIds.size());
        assertTrue(operations.readDataIds.stream()
                .allMatch(properties.getRouteAccessDefinitionDataId()::equals));
    }

    @Test
    void shouldRejectStaleDefinitionRevision() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations();
        IngressAccessContent content = PolicyTestFixtures.snapshot(1, true).content();
        operations.values.put(properties.getAppDefinitionDataId(),
                codec.encodeApps(content.appPolicies()));
        NacosAppPolicyDefinitionRepository repository =
                new NacosAppPolicyDefinitionRepository(operations, properties, codec);

        assertFalse(repository.compareAndSet("stale", content.appPolicies()));
        assertEquals(content.appPolicies(), repository.load().value());
    }

    @Test void shouldCasIndependentPolicyConfigurationDefinition() {
        var operations = new FakeNacosConfigOperations();
        com.cloud.gateway.domain.policy.bundle.PolicyComposition empty = com.cloud.gateway.domain.policy.bundle.PolicyComposition.empty();
        operations.values.put(properties.getPolicyConfigurationDefinitionDataId(), codec.encodePolicyConfiguration(empty));
        var repository = new NacosPolicyCompositionDefinitionRepository(operations, properties, codec);
        VersionedPolicyDefinition<com.cloud.gateway.domain.policy.bundle.PolicyComposition> observed = repository.load();
        var id = new com.cloud.gateway.domain.policy.bundle.PolicyBundleId("standard");
        var changed = new com.cloud.gateway.domain.policy.bundle.PolicyComposition(Map.of(id,
                new com.cloud.gateway.domain.policy.bundle.PolicyBundle(id, null,
                        new com.cloud.gateway.domain.policy.capability.TimeoutPolicy(java.time.Duration.ofSeconds(1),
                                java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(3)), null, null, null)), java.util.List.of());
        assertTrue(repository.compareAndSet(observed.sourceRevision(), changed));
        assertFalse(repository.compareAndSet(observed.sourceRevision(), empty));
        assertEquals(changed, repository.load().value());
        assertEquals(java.util.Set.of(properties.getPolicyConfigurationDefinitionDataId()), operations.values.keySet());
    }

    @Test void shouldUseOneClientDefinitionRevisionForAccessAndCredentials() {
        var operations = new FakeNacosConfigOperations();
        var clientId = new ClientId("partner");
        var client = new ClientPolicy(clientId,
                java.util.Set.of(new AppId("web")));
        operations.values.put(properties.getClientDefinitionDataId(), codec.encodeClients(Map.of(clientId, client)));
        var repository = new NacosClientPolicyDefinitionRepository(operations, properties, codec);
        VersionedPolicyDefinition<Map<ClientId,
                ClientPolicy>> observed = repository.load();
        var active = new Credential(
                new CredentialId("active"), clientId,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-reference",
                java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.Instant.parse("2030-01-01T00:00:00Z"));
        ClientPolicy updated = client.withCredentials(
                new Credentials(active, null));
        assertTrue(repository.compareAndSet(observed.sourceRevision(), Map.of(clientId, updated)));
        assertFalse(repository.compareAndSet(observed.sourceRevision(), Map.of(clientId,
                client.withAllowedApps(java.util.Set.of(), ClientStatus.DISABLED))));
        assertEquals(updated, repository.load().value().get(clientId));
        assertEquals(java.util.Set.of("gateway-client-policies.yaml"), operations.values.keySet());
    }

    private static final class FakeNacosConfigOperations implements NacosConfigOperations {
        private final Map<String, String> values = new HashMap<>();
        private final java.util.List<String> readDataIds = new java.util.ArrayList<>();

        @Override public String getConfig(String dataId, String group, long timeoutMillis) {
            readDataIds.add(dataId);
            return values.get(dataId);
        }

        @Override public String getConfigAndListen(String dataId, String group, long timeoutMillis,
                Consumer<String> changeCallback) {
            return values.get(dataId);
        }

        @Override public boolean publishConfigCas(String dataId, String group, String content, String expectedMd5) {
            String observed = values.get(dataId);
            if (!NacosPolicySnapshotStore.sourceRevision(observed).equals(expectedMd5)) {
                return false;
            }
            values.put(dataId, content);
            return true;
        }

        @Override public void removeListener(String dataId, String group, Consumer<String> changeCallback) { }
    }
}
