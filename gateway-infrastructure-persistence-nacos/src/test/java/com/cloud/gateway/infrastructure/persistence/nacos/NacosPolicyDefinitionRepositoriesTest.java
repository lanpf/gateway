package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.CapabilityBundle;
import com.cloud.gateway.domain.policy.bundle.CapabilityBundleId;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.domain.policy.capability.TimeoutPolicy;
import com.cloud.gateway.domain.policy.header.HeaderRules;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class NacosPolicyDefinitionRepositoriesTest {
    private final NacosPolicyProperties properties = new NacosPolicyProperties();
    private final StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
            new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl())), new ObjectMapper().findAndRegisterModules(),
            HeaderRules.registeredOnly());

    @Test
    void shouldLoadAndUpdateOnlyEndpointDefinitionDataId() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations();
        IngressContent content = PolicyTestFixtures.snapshot(1, true).content();
        operations.values.put(properties.getEndpointDefinitionDataId(),
                codec.encodeEndpointRegistry(content.endpointRegistry()));
        NacosEndpointAccessDefinitionRepository repository =
                new NacosEndpointAccessDefinitionRepository(operations, properties, codec);

        RevisionedAccessDefinition<EndpointRegistry> observed = repository.load();
        EndpointRegistry updated = PolicyTestFixtures.snapshot(2, false).content().endpointRegistry();

        assertNotNull(repository.compareAndSet(observed.sourceRevision(), updated));
        assertFalse(repository.load().value().find(new EndpointId("USER_AUTH.LOGIN")).orElseThrow().enabled());
        assertEquals(2, operations.readDataIds.size());
        assertTrue(operations.readDataIds.stream()
                .allMatch(properties.getEndpointDefinitionDataId()::equals));
    }

    @Test
    void shouldRejectStaleDefinitionRevision() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations();
        IngressContent content = PolicyTestFixtures.snapshot(1, true).content();
        operations.values.put(properties.getAppDefinitionDataId(),
                codec.encodeApps(content.appRegistry()));
        NacosAppAccessDefinitionRepository repository =
                new NacosAppAccessDefinitionRepository(operations, properties, codec);

        assertNull(repository.compareAndSet(new com.cloud.gateway.domain.ingress.snapshot.Revision("stale"), content.appRegistry()));
        assertEquals(content.appRegistry().asMap(), repository.load().value().asMap());
    }

    @Test void shouldCasIndependentCapabilityCompositionDefinition() {
        var operations = new FakeNacosConfigOperations();
        CapabilityComposition empty = CapabilityComposition.empty();
        operations.values.put(properties.getPolicyConfigurationDefinitionDataId(),
                codec.encodeCapabilityComposition(empty));
        var repository = new NacosCapabilityCompositionDefinitionRepository(operations, properties, codec);
        RevisionedAccessDefinition<CapabilityComposition> observed = repository.load();
        var id = new CapabilityBundleId("standard");
        var changed = new CapabilityComposition(Map.of(id, new CapabilityBundle(id, null,
                new TimeoutPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)),
                null, null, null)), List.of());
        assertNotNull(repository.compareAndSet(observed.sourceRevision(), changed));
        assertNull(repository.compareAndSet(observed.sourceRevision(), empty));
        assertEquals(changed, repository.load().value());
        assertEquals(Set.of(properties.getPolicyConfigurationDefinitionDataId()), operations.values.keySet());
    }

    @Test void shouldUseOneClientDefinitionRevisionForAccessAndCredentials() {
        var operations = new FakeNacosConfigOperations();
        var clientId = new ClientId("partner");
        var clients = new ClientRegistry(List.of(new Client(clientId, ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("web")), null))));
        operations.values.put(properties.getClientDefinitionDataId(), codec.encodeClients(clients));
        var repository = new NacosClientAccessDefinitionRepository(operations, properties, codec);
        RevisionedAccessDefinition<ClientRegistry> observed = repository.load();

        var active = new Credential(new CredentialId("active"), "secret-reference",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
        var credentials = new Credentials(active, null);
        var updated = new ClientRegistry(List.of(new Client(clientId, ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("web")), credentials))));

        assertNotNull(repository.compareAndSet(observed.sourceRevision(), updated));
        assertNull(repository.compareAndSet(observed.sourceRevision(), clients));
        assertEquals(credentials, repository.load().value().find(clientId).orElseThrow().access().credentials());
        assertEquals(Set.of("gateway-client-capabilityComposition.yaml"), operations.values.keySet());
    }

    private static final class FakeNacosConfigOperations implements NacosConfigOperations {
        private final Map<String, String> values = new HashMap<>();
        private final List<String> readDataIds = new ArrayList<>();

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
            if (observed == null || !NacosAccessSnapshotStore.sourceRevision(observed).equals(expectedMd5)) {
                return false;
            }
            values.put(dataId, content);
            return true;
        }

        @Override public void removeListener(String dataId, String group, Consumer<String> changeCallback) { }
    }
}
