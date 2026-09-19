package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AppPropertiesMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.GatewayPolicyDefinitionProperties;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionPropertiesMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PropertiesPolicyDefinitionRepositoriesTest {
    @Test void shouldReadCapabilityCompositionAndChangeItsRevisionWithoutOtherDefinitions() {
        var properties = new GatewayPolicyDefinitionProperties();
        var bundle = new com.cloud.gateway.infrastructure.policy.config.CapabilityCompositionProperties.Bundle();
        bundle.setBundleId("standard");
        var timeout = new com.cloud.gateway.infrastructure.policy.config.CapabilityCompositionProperties.Timeout();
        timeout.setConnect(java.time.Duration.ofSeconds(1));
        timeout.setRead(java.time.Duration.ofSeconds(2));
        timeout.setTotal(java.time.Duration.ofSeconds(3));
        bundle.setTimeout(timeout);
        properties.getPolicies().getBundles().put("standard", bundle);
        var mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl()));
        CapabilityCompositionPropertiesMapStructMapper policyConfiguration = org.mapstruct.factory.Mappers.getMapper(
                com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionPropertiesMapStructMapper.class);
        var codec = new StoredPolicyDefinitionCodec(mapper, new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        var repository = new PropertiesCapabilityCompositionDefinitionRepository(properties, mapper, policyConfiguration, codec);
        RevisionedAccessDefinition<CapabilityComposition> first = repository.load();
        timeout.setTotal(java.time.Duration.ofSeconds(4));
        RevisionedAccessDefinition<CapabilityComposition> second = repository.load();
        org.junit.jupiter.api.Assertions.assertNotEquals(first.sourceRevision(), second.sourceRevision());
        assertEquals(java.time.Duration.ofSeconds(4), second.value().bundles()
                .get(new com.cloud.gateway.domain.policy.bundle.CapabilityBundleId("standard")).timeout().total());
        assertEquals(java.time.Duration.ofSeconds(3), first.value().bundles()
                .get(new com.cloud.gateway.domain.policy.bundle.CapabilityBundleId("standard")).timeout().total());
    }

    @Test
    void shouldLoadAppsWithoutConvertingUnrelatedDefinitions() {
        GatewayPolicyDefinitionProperties properties = new GatewayPolicyDefinitionProperties();
        GatewayPolicyDefinitionProperties.App app = new GatewayPolicyDefinitionProperties.App();
        app.setStatus(AppStatus.ENABLED);
        app.setPlatform("WEB");
        app.getAllowedOrigins().add("https://browser.example.com");
        properties.getApps().put("web-app", app);
        properties.getClients().put(
                "unrelated-invalid-client", new GatewayPolicyDefinitionProperties.Client());
        StoredAccessSnapshotMapper mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl()));
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                mapper, new ObjectMapper().findAndRegisterModules(),
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        PropertiesAppAccessDefinitionRepository repository =
                new PropertiesAppAccessDefinitionRepository(properties, mapper, new AppPropertiesMapStructMapperImpl(), codec);

        RevisionedAccessDefinition<AppRegistry> observed = repository.load();

        assertEquals("WEB", observed.value().find(new AppId("web-app")).orElseThrow().platform());
        assertEquals(Set.of("https://browser.example.com"),
                observed.value().find(new AppId("web-app")).orElseThrow().access().allowedOrigins());
    }
}
