package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosClientAppPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosPolicySnapshotStore;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosRouteAccessPolicyDefinitionRepository;
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
            new StoredPolicySnapshotMapper(), new ObjectMapper().findAndRegisterModules());

    @Test
    void shouldLoadAndUpdateOnlyRouteDefinitionDataId() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations();
        IngressPolicyContent content = PolicyTestFixtures.snapshot(1, true).content();
        operations.values.put(properties.getRouteAccessDefinitionDataId(),
                codec.encodeRouteAccess(content.routeAccessPolicies()));
        NacosRouteAccessPolicyDefinitionRepository repository =
                new NacosRouteAccessPolicyDefinitionRepository(operations, properties, codec);

        VersionedPolicyDefinition<Map<EndpointId, IngressRouteAccessPolicy>> observed = repository.load();
        Map<EndpointId, IngressRouteAccessPolicy> updated =
                PolicyTestFixtures.snapshot(2, false).content().routeAccessPolicies();

        assertTrue(repository.compareAndSet(observed.sourceRevision(), updated));
        assertFalse(repository.load().value().get(new EndpointId("USER_AUTH.LOGIN")).enabled());
        assertEquals(2, operations.readDataIds.size());
        assertTrue(operations.readDataIds.stream()
                .allMatch(properties.getRouteAccessDefinitionDataId()::equals));
    }

    @Test
    void shouldRejectStaleDefinitionRevision() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations();
        IngressPolicyContent content = PolicyTestFixtures.snapshot(1, true).content();
        operations.values.put(properties.getClientAppDefinitionDataId(),
                codec.encodeClientApps(content.clientAppPolicies()));
        NacosClientAppPolicyDefinitionRepository repository =
                new NacosClientAppPolicyDefinitionRepository(operations, properties, codec);

        assertFalse(repository.compareAndSet("stale", content.clientAppPolicies()));
        assertEquals(content.clientAppPolicies(), repository.load().value());
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
